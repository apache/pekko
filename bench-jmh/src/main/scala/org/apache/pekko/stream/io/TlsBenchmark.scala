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

package org.apache.pekko.stream.io

import java.security.{ KeyStore, SecureRandom }
import java.util.concurrent.TimeUnit
import javax.net.ssl.{ KeyManagerFactory, SSLContext, SSLEngine, SSLSession, TrustManagerFactory }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Success, Try }

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream._
import pekko.stream.TLSProtocol._
import pekko.stream.impl.io.{ TlsGraphStage, TlsModule }
import pekko.stream.scaladsl._
import pekko.util.ByteString

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * JMH benchmark comparing the legacy actor-based TLS path (`TlsModule`) to the
 * GraphStage path (`TlsGraphStage`).
 *
 *  - `warmRoundTrip` drives a fixed payload through a client+server echo loop, with
 *    the SSL engines reused across invocations (one materialization per @Setup).
 *    This isolates per-record encrypt/decrypt overhead — handshake cost is amortized
 *    away by the iteration count.
 *  - `coldHandshake` measures the cost of materializing a fresh client+server pair
 *    and completing the TLS handshake before transferring a tiny payload. This
 *    represents short-lived connections (e.g. HTTPS request/response).
 *
 * Run with:
 * {{{
 *   sbt "bench-jmh/Jmh/run -i 5 -wi 3 -f1 -t1 .*TlsBenchmark.*"
 * }}}
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class TlsBenchmark {

  private val config: Config = ConfigFactory.parseString("""
      pekko {
        log-config-on-start = off
        log-dead-letters-during-shutdown = off
        stdout-loglevel = "OFF"
        loglevel = "OFF"
        actor.default-dispatcher {
          throughput = 1024
        }
        actor.default-mailbox {
          mailbox-type = "org.apache.pekko.dispatch.SingleConsumerOnlyUnboundedMailbox"
        }
      }""".stripMargin).withFallback(ConfigFactory.load())

  implicit var system: ActorSystem = _
  private var sslContext: SSLContext = _
  private var ciphers: Array[String] = _

  @Param(Array("legacy", "graphstage"))
  var implementation: String = _

  // 256 B = control message; 4 KiB = typical HTTP request; 64 KiB = streaming chunk
  @Param(Array("256", "4096", "65536"))
  var payloadSize: Int = _

  private var payload: ByteString = _
  private var payloads: scala.collection.immutable.IndexedSeq[SslTlsOutbound] = _

  @Setup
  def setup(): Unit = {
    system = ActorSystem("TlsBenchmark", config)
    SystemMaterializer(system).materializer

    sslContext = TlsBenchmark.initSslContext("TLSv1.2")
    ciphers = TlsBenchmark.TLS12Ciphers.toArray

    payload = ByteString(Array.fill[Byte](payloadSize)('a'.toByte))
    payloads = (0 until TlsBenchmark.WarmRoundTripRecords).map(_ => SendBytes(payload))
  }

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }

  private def engine(role: TLSRole): SSLEngine = {
    val e = sslContext.createSSLEngine()
    e.setUseClientMode(role == Client)
    e.setEnabledCipherSuites(ciphers)
    e.setEnabledProtocols(Array("TLSv1.2"))
    e
  }

  private def makeBidi(
      role: TLSRole,
      closing: TLSClosing,
      verifySession: SSLSession => Try[Unit] = _ => Success(()))
      : BidiFlow[SslTlsOutbound, ByteString, ByteString, SslTlsInbound, NotUsed] =
    implementation match {
      case "legacy" =>
        BidiFlow.fromGraph(
          TlsModule(Attributes.none, () => engine(role), verifySession, closing))
      case "graphstage" =>
        graphStageBidi(role, closing, verifySession)
    }

  private def graphStageBidi(
      role: TLSRole,
      closing: TLSClosing,
      verifySession: SSLSession => Try[Unit])
      : BidiFlow[SslTlsOutbound, ByteString, ByteString, SslTlsInbound, NotUsed] =
    BidiFlow
      .fromGraph(new TlsGraphStage(() => engine(role), verifySession, closing))
      .withAttributes(TlsGraphStage.StreamTlsAttributes)

  /**
   * Warm round-trip: 1000 payloads through a fresh client+server pair. The
   * handshake is amortized over the records and the sink counts bytes instead
   * of concatenating payloads, keeping the measurement focused on TLS work.
   */
  @Benchmark
  @OperationsPerInvocation(1000)
  def warmRoundTrip(): Unit = {
    val records = TlsBenchmark.WarmRoundTripRecords
    val expected = payload.size * records
    val client = makeBidi(Client, IgnoreComplete)
    val server = makeBidi(Server, IgnoreComplete)
    val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }

    val done = Source(payloads)
      .via(client.atop(server.reversed).join(echo))
      .collect { case SessionBytes(_, b) => b }
      .scan(0)((acc, b) => acc + b.size)
      .dropWhile(_ < expected)
      .runWith(Sink.headOption)

    Await.result(done, 30.seconds)
  }

  /**
   * Cold handshake: each invocation builds a fresh client+server pair and
   * completes the handshake by exchanging one configured payload. The sink
   * counts bytes only, which avoids charging ByteString concatenation to the
   * TLS implementation being tested.
   */
  @Benchmark
  def coldHandshake(): Unit = {
    val client = makeBidi(Client, IgnoreComplete)
    val server = makeBidi(Server, IgnoreComplete)
    val expected = payload.size
    val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }

    val done = Source
      .single[SslTlsOutbound](SendBytes(payload))
      .via(client.atop(server.reversed).join(echo))
      .collect { case SessionBytes(_, b) => b }
      .scan(0)((acc, b) => acc + b.size)
      .dropWhile(_ < expected)
      .runWith(Sink.headOption)

    Await.result(done, 30.seconds)
  }
}

object TlsBenchmark {

  final val WarmRoundTripRecords = 1000

  val TLS12Ciphers: Set[String] = Set(
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")

  def initSslContext(protocol: String): SSLContext = {
    val password = "changeme"

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(getClass.getResourceAsStream("/keystore"), password.toCharArray)

    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
    trustStore.load(getClass.getResourceAsStream("/truststore"), password.toCharArray)

    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(keyStore, password.toCharArray)

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(trustStore)

    val context = SSLContext.getInstance(protocol)
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }
}
