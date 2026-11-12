/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.scaladsl

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.net.ssl.{ SSLEngine, SSLSession }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Success

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream.TLSProtocol._
import pekko.stream._
import pekko.stream.io.TlsSpec
import pekko.testkit.PekkoSpec
import pekko.testkit.TestDuration
import pekko.util.ByteString

class TlsEngineSelectionSpec extends PekkoSpec {

  private def checkInFreshJvm(
      configValue: Option[String],
      property: Option[String],
      expected: String,
      environmentOverride: Option[(String, Boolean)] = None): Unit = {
    val config = Files.createTempFile("pekko-tls-selection", ".conf")
    val output = Files.createTempFile("pekko-tls-selection", ".log")
    try {
      Files.write(config,
        configValue.fold("")(value => s"pekko.stream.materializer.tls.engine = $value").getBytes(UTF_8))
      val command = Seq(
        new File(new File(System.getProperty("java.home"), "bin"), "java").getPath,
        "-cp",
        System.getProperty("java.class.path"),
        s"-Dconfig.file=$config") ++
        property.map(value => s"-Dpekko.stream.materializer.tls.engine=$value") ++
        environmentOverride.map { case (_, enabled) => s"-Dconfig.override_with_env_vars=$enabled" } ++
        Seq("org.apache.pekko.stream.scaladsl.TlsEngineSelectionProbe", expected)
      val builder = new ProcessBuilder(command.asJava).redirectErrorStream(true).redirectOutput(output.toFile)
      builder.environment().remove("CONFIG_FORCE_pekko_stream_materializer_tls_engine")
      environmentOverride.foreach { case (value, _) =>
        builder.environment().put("CONFIG_FORCE_pekko_stream_materializer_tls_engine", value)
      }
      val process = builder.start()
      try {
        val completed = process.waitFor(60.seconds.dilated.toMillis, TimeUnit.MILLISECONDS)
        withClue(new String(Files.readAllBytes(output), UTF_8)) {
          completed shouldBe true
          process.exitValue() shouldBe 0
        }
      } finally {
        if (process.isAlive) {
          process.destroyForcibly()
          process.waitFor(10, TimeUnit.SECONDS)
        }
      }
    } finally {
      Files.deleteIfExists(config)
      Files.deleteIfExists(output)
    }
  }

  "TLS engine selection" should {

    // TLS chooses its engine once per JVM. Exercise the public Scala and Java
    // factories in separate processes rather than mutate this JVM's singleton.
    for ((description, config, property, expected) <- Seq(
        ("use the reference default", None, None, "legacy-actor"),
        ("fall back when the config key is missing", Some("null"), None, "legacy-actor"),
        ("honor explicit graph-stage config", Some("graph-stage"), None, "graph-stage"),
        ("retain explicit legacy config", Some("legacy-actor"), None, "legacy-actor"),
        ("let a legacy property override graph-stage config", Some("graph-stage"), Some("legacy-actor"),
          "legacy-actor"),
        ("let a graph-stage property override legacy config", Some("legacy-actor"), Some("graph-stage"), "graph-stage"),
        ("trim a non-blank property", Some("legacy-actor"), Some(" graph-stage "), "graph-stage"),
        ("override an unresolved substitution", Some("${nonexistent}"), Some("graph-stage"), "graph-stage"),
        ("override a config value of the wrong type", Some("[graph-stage]"), Some("legacy-actor"), "legacy-actor"),
        ("use the reference default with an empty property", None, Some(""), "legacy-actor"),
        ("use the missing-key fallback with a blank property", Some("null"), Some("  "), "legacy-actor"),
        ("resolve application substitutions with a blank property", Some("${selection}\nselection = legacy-actor"),
          Some("  "), "legacy-actor"),
        ("ignore an empty property", Some("legacy-actor"), Some(""), "legacy-actor"),
        ("ignore a whitespace-only property", Some("legacy-actor"), Some("  "), "legacy-actor"),
        ("reject unsupported config", Some("unsupported"), None, "invalid"),
        ("reject an unsupported overriding property", Some("graph-stage"), Some("unsupported"), "invalid"))) {
      description in {
        checkInFreshJvm(config, property, expected)
      }
    }

    "honor an enabled environment override" in {
      checkInFreshJvm(Some("graph-stage"), None, "legacy-actor", Some("legacy-actor" -> true))
    }

    "preserve an environment override when the system property is blank" in {
      checkInFreshJvm(Some("graph-stage"), Some("  "), "legacy-actor", Some("legacy-actor" -> true))
    }

    "prefer a non-blank system property over the environment override" in {
      checkInFreshJvm(Some("legacy-actor"), Some("graph-stage"), "graph-stage", Some("legacy-actor" -> true))
    }

    "ignore a disabled environment override when the system property is blank" in {
      checkInFreshJvm(Some("graph-stage"), Some("  "), "graph-stage", Some("legacy-actor" -> false))
    }

    "ignore blank system properties and use the configured value" in {
      TLS.configuredEngineName(Some(""), TLS.GraphStageEngineName) shouldBe TLS.GraphStageEngineName
      TLS.configuredEngineName(Some("  "), TLS.LegacyActorEngineName) shouldBe TLS.LegacyActorEngineName
    }

    "let non-blank system properties override the configured value" in {
      TLS.configuredEngineName(Some(TLS.GraphStageEngineName), TLS.LegacyActorEngineName) shouldBe
      TLS.GraphStageEngineName
    }
  }
}

/** Public factory checks isolated from the parent test JVM's TLS singleton. */
object TlsEngineSelectionProbe {
  private type TlsBidi = BidiFlow[SslTlsOutbound, ByteString, ByteString, SslTlsInbound, NotUsed]

  def main(args: Array[String]): Unit = {
    val expected = args(0)
    val unusedEngine: () => SSLEngine = () => throw new AssertionError("The engine factory must be lazy")
    if (expected == "invalid") {
      try {
        TLS(unusedEngine, IgnoreComplete)
        throw new AssertionError("Unsupported engine was accepted")
      } catch {
        case error: ExceptionInInitializerError =>
          require(error.getCause.isInstanceOf[IllegalArgumentException])
          require(error.getCause.getMessage.contains("Unsupported TLS engine [unsupported]"))
      }
    } else {
      val shapes = Seq(
        TLS(unusedEngine, IgnoreComplete),
        TLS(unusedEngine, (_: SSLSession) => Success(()), IgnoreComplete),
        javadsl.TLS.create(() => unusedEngine(), IgnoreComplete).asScala,
        javadsl.TLS.create(() => unusedEngine(), (_: SSLSession) => (), IgnoreComplete).asScala)
      shapes.foreach { graph =>
        require(graph.getAttributes.contains(Attributes.AsyncBoundary) == (expected == "graph-stage"))
        val port = graph.shape.in1.s
        require(if (expected == "graph-stage") port == "TlsGraphStage.plainIn" else port.endsWith(".transportIn"))
      }

      implicit val system: ActorSystem = ActorSystem("TlsEngineSelectionProbe")
      try {
        for (protocol <- Seq("TLSv1.2", "TLSv1.3")) {
          val context = TlsSpec.initSslContext(protocol)
          def engine(client: Boolean): SSLEngine = {
            val sslEngine = context.createSSLEngine()
            sslEngine.setUseClientMode(client)
            sslEngine.setEnabledProtocols(Array(protocol))
            sslEngine
          }
          def roundTrip(client: TlsBidi, server: TlsBidi): Unit = {
            val payload = ByteString(s"$expected $protocol public TLS factories")
            val echo = Flow[SslTlsInbound].collect { case SessionBytes(session, bytes) =>
              require(session.getProtocol == protocol)
              SendBytes(bytes)
            }
            val result = Source.single[SslTlsOutbound](SendBytes(payload))
              .via(client.atop(server.reversed).join(echo))
              .collect { case SessionBytes(session, bytes) =>
                require(session.getProtocol == protocol)
                bytes
              }
              .scan(ByteString.empty)(_ ++ _)
              .dropWhile(_.size < payload.size)
              .runWith(Sink.head)
            require(Await.result(result, 15.seconds) == payload)
          }
          roundTrip(
            TLS(() => engine(true), (_: SSLSession) => Success(()), IgnoreComplete),
            javadsl.TLS.create(() => engine(false), (_: SSLSession) => (), IgnoreComplete).asScala)
          roundTrip(
            javadsl.TLS.create(() => engine(true), IgnoreComplete).asScala,
            TLS(() => engine(false), IgnoreComplete))
        }
      } finally {
        system.terminate()
        Await.ready(system.whenTerminated, 15.seconds)
      }
    }
  }
}
