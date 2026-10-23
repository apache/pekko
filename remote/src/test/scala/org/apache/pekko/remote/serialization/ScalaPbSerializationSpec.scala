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

package org.apache.pekko.remote.serialization

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import com.google.protobuf.timestamp.Timestamp

import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem }
import pekko.serialization.{ SerializationExtension, Serializers }
import pekko.testkit.PekkoSpec

import com.typesafe.config.ConfigFactory

class ScalaPbOverrideSerializer(system: ExtendedActorSystem) extends ProtobufSerializer(system)

object ScalaPbAbsentClasspath {
  def main(args: Array[String]): Unit = {
    require(args.isEmpty)
    require(scala.util.Try(Class.forName("scalapb.GeneratedMessage")).isFailure, "ScalaPB must be absent")
    val system = ActorSystem("without-scalapb")
    try {
      val serialization = SerializationExtension(system)
      require(serialization.deserialize(serialization.serialize("still works").get, classOf[String]).get ==
        "still works")
      println("ActorSystem and serialization started without ScalaPB")
    } finally Await.result(system.terminate(), 15.seconds)
  }
}

class ScalaPbSerializationSpec extends PekkoSpec {
  private val serialization = SerializationExtension(system)
  private val message = Timestamp(seconds = 123456789L, nanos = 123456789)

  "Optional ScalaPB serialization" must {
    "select the default binding and round-trip an actual generated message" in {
      serialization.findSerializerFor(message).getClass shouldBe classOf[ProtobufSerializer]
      val bytes = serialization.serialize(message).get
      serialization.deserialize(bytes, classOf[Timestamp]).get shouldBe message
      val serializer = serialization.findSerializerFor(message)
      serialization.deserialize(bytes, serializer.identifier,
        Serializers.manifestFor(serializer, message)).get shouldBe message
    }

    "honor an application override of the marker binding" in {
      val config = ConfigFactory.parseString(s"""
        pekko.actor.serializers.scalapb-custom = "${classOf[ScalaPbOverrideSerializer].getName}"
        pekko.actor.serialization-identifiers."${classOf[ScalaPbOverrideSerializer].getName}" = 987654
        pekko.actor.serialization-bindings."scalapb.GeneratedMessage" = scalapb-custom
        """).withFallback(system.settings.config)
      val other = ActorSystem("scalapb-override", config)
      try {
        val overridden = SerializationExtension(other)
        overridden.findSerializerFor(message).getClass shouldBe classOf[ScalaPbOverrideSerializer]
        overridden.deserialize(overridden.serialize(message).get, classOf[Timestamp]).get shouldBe message
      } finally shutdown(other)
    }

    "honor disabling the marker binding" in {
      val config = ConfigFactory.parseString("""
        pekko.actor.serialization-bindings."scalapb.GeneratedMessage" = none
        """).withFallback(system.settings.config)
      val other = ActorSystem("scalapb-disabled", config)
      try {
        val disabled = SerializationExtension(other)
        disabled.findSerializerFor(message) should not be a[ProtobufSerializer]
        disabled.serialize(message).isFailure shouldBe true
      } finally shutdown(other)
    }

    "start with a real classpath that does not contain the optional runtime" in {
      val classpath = System.getProperty("java.class.path").split(File.pathSeparator).toVector
      val runtime = new File(classOf[scalapb.GeneratedMessage].getProtectionDomain.getCodeSource.getLocation.toURI)
      classpath.map(new File(_).getCanonicalFile) should contain(runtime.getCanonicalFile)
      val withoutRuntime = classpath.filterNot(path => new File(path).getCanonicalFile == runtime.getCanonicalFile)
      val output = Files.createTempFile("pekko-without-scalapb-", ".log")
      val java = new File(new File(System.getProperty("java.home"), "bin"), "java").getPath
      val process = new ProcessBuilder(
        java,
        "-Xmx256m",
        "-cp",
        withoutRuntime.mkString(File.pathSeparator),
        ScalaPbAbsentClasspath.getClass.getName.stripSuffix("$"))
        .redirectErrorStream(true)
        .redirectOutput(output.toFile)
        .start()
      try {
        val exited = process.waitFor(30, TimeUnit.SECONDS)
        withClue(Files.readString(output)) {
          exited shouldBe true
          process.exitValue() shouldBe 0
          Files.readString(output) should include("ActorSystem and serialization started without ScalaPB")
        }
      } finally {
        if (process.isAlive) process.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
        Files.deleteIfExists(output)
      }
    }
  }
}
