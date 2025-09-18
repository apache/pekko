/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.serialization

import java.util.{ BitSet => ProgrammaticJavaDummy }
import java.util.{ Date => SerializableDummy }
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko
import pekko.actor.{ ActorSystem, BootstrapSetup, ExtendedActorSystem }
import pekko.actor.setup.ActorSystemSetup
import pekko.testkit.{ PekkoSpec, TestKit }

import com.typesafe.config.ConfigFactory

class ConfigurationDummy
class ProgrammaticDummy

/**
 * Keeps a registry of each object "serialized", returns an identifier, for every "deserialization" the original object
 * is returned. Useful for tests needing serialization back and forth but that must avoid java serialization for some
 * reason.
 */
final class FakeSerializer extends Serializer {
  val includeManifest = false
  val identifier = 666
  private val registry = new ConcurrentHashMap[Integer, AnyRef]()
  private val counter = new AtomicInteger(0)

  def toBinary(o: AnyRef) = {
    val id = counter.addAndGet(1)
    require(id < Byte.MaxValue)
    registry.put(id, o)
    Array(id.toByte)
  }

  def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]) = {
    require(bytes.length == 1)
    val id = bytes(0).toInt
    registry.get(id)
  }
}

object SerializationSetupSpec {

  val programmaticDummySerializer = new FakeSerializer
  val testSerializer = new NoopSerializer

  val serializationSettings = SerializationSetup { _ =>
    List(SerializerDetails("test", programmaticDummySerializer, List(classOf[ProgrammaticDummy])))
  }
  val bootstrapSettings = BootstrapSetup(
    None,
    Some(ConfigFactory.parseString("""
    pekko {
      actor {
        allow-java-serialization = on

        # this is by default on, but tests are running with off, use defaults here
        warn-about-java-serializer-usage = on

        serialization-bindings {
          "org.apache.pekko.serialization.ConfigurationDummy" = test
        }
      }
    }
    """)),
    None)
  val actorSystemSettings = ActorSystemSetup(bootstrapSettings, serializationSettings)

  val noJavaSerializationSystem = ActorSystem(
    "SerializationSettingsSpec" + "NoJavaSerialization",
    ConfigFactory.parseString("""
    pekko {
      actor {
        allow-java-serialization = off
        # this is by default on, but tests are running with off, use defaults here
        warn-about-java-serializer-usage = on
      }
    }
    """.stripMargin))
  val noJavaSerializer = new DisabledJavaSerializer(noJavaSerializationSystem.asInstanceOf[ExtendedActorSystem])

}

class SerializationSetupSpec
    extends PekkoSpec(ActorSystem("SerializationSettingsSpec", SerializationSetupSpec.actorSystemSettings)) {

  import SerializationSetupSpec._

  private def verifySerialization(sys: ActorSystem, obj: AnyRef): Unit = {
    val serialization = SerializationExtension(sys)
    val bytes = serialization.serialize(obj).get
    val serializer = serialization.findSerializerFor(obj)
    val manifest = Serializers.manifestFor(serializer, obj)
    serialization.deserialize(bytes, serializer.identifier, manifest).get
  }

  "The serialization settings" should {

    "allow for programmatic configuration of serializers" in {
      val serializer = SerializationExtension(system).findSerializerFor(new ProgrammaticDummy)
      serializer shouldBe theSameInstanceAs(programmaticDummySerializer)
    }

    "allow a configured binding to hook up to a programmatic serializer" in {
      val serializer = SerializationExtension(system).findSerializerFor(new ConfigurationDummy)
      serializer shouldBe theSameInstanceAs(programmaticDummySerializer)
    }

    "fail during ActorSystem creation when misconfigured" in {
      val config =
        ConfigFactory.parseString("""
             pekko.loglevel = OFF
             pekko.stdout-loglevel = OFF
             pekko.actor.serializers.doe = "john.is.not.here"
          """).withFallback(ConfigFactory.load())

      a[ClassNotFoundException] should be thrownBy {
        val system = ActorSystem("SerializationSetupSpec-FailingSystem", config)
        system.terminate()
      }
    }

  }

  // This is a weird edge case, someone creating a JavaSerializer manually and using it in a system means
  // that they'd need a different actor system to be able to create it... someone MAY pick a system with
  // allow-java-serialization=on to create the SerializationSetup and use that SerializationSetup
  // in another system with allow-java-serialization=off
  val addedJavaSerializationSettings = SerializationSetup { _ =>
    List(
      SerializerDetails("test", programmaticDummySerializer, List(classOf[ProgrammaticDummy])),
      SerializerDetails(
        "java-manual",
        new JavaSerializer(system.asInstanceOf[ExtendedActorSystem]),
        List(classOf[ProgrammaticJavaDummy])))
  }
  val addedJavaSerializationProgramaticallyButDisabledSettings = BootstrapSetup(
    None,
    Some(ConfigFactory.parseString("""
    pekko {
      loglevel = debug
      actor {
        allow-java-serialization = off
        # this is by default on, but tests are running with off, use defaults here
        warn-about-java-serializer-usage = on
      }
    }
    """)),
    None)

  val addedJavaSerializationViaSettingsSystem =
    ActorSystem(
      "addedJavaSerializationSystem",
      ActorSystemSetup(addedJavaSerializationProgramaticallyButDisabledSettings, addedJavaSerializationSettings))

  "Disabling java serialization" should {

    "throw if passed system to JavaSerializer has allow-java-serialization = off" in {
      intercept[DisabledJavaSerializer.JavaSerializationException] {
        new JavaSerializer(noJavaSerializationSystem.asInstanceOf[ExtendedActorSystem])
      }.getMessage should include("pekko.actor.allow-java-serialization = off")

      intercept[DisabledJavaSerializer.JavaSerializationException] {
        SerializationExtension(addedJavaSerializationViaSettingsSystem)
          .findSerializerFor(new ProgrammaticJavaDummy)
          .toBinary(new ProgrammaticJavaDummy)
      }
    }

    "have replaced java serializer" in {
      // allow-java-serialization = on in `system`
      val serializer = SerializationExtension(system).findSerializerFor(new ProgrammaticJavaDummy)
      serializer.getClass should ===(classOf[JavaSerializer])

      // should not allow deserialization, it would have been java serialization!
      val serializer2 =
        SerializationExtension(addedJavaSerializationViaSettingsSystem).findSerializerFor(new ProgrammaticJavaDummy)
      serializer2.getClass should ===(classOf[DisabledJavaSerializer])
      serializer2.identifier should ===(serializer.identifier)

      verifySerialization(system, new ProgrammaticDummy)
      verifySerialization(addedJavaSerializationViaSettingsSystem, new ProgrammaticDummy)
    }

    "disable java serialization also for incoming messages if serializer id usually would have found the serializer" in {
      val ser1 = SerializationExtension(system)
      val msg = new SerializableDummy
      val bytes = ser1.serialize(msg).get
      val serId = ser1.findSerializerFor(msg).identifier
      ser1.findSerializerFor(msg).includeManifest should ===(false)

      val ser2 = SerializationExtension(noJavaSerializationSystem)
      ser2.findSerializerFor(new SerializableDummy) should ===(noJavaSerializer)
      ser2.serializerByIdentity(serId) should ===(noJavaSerializer)
      intercept[DisabledJavaSerializer.JavaSerializationException] {
        ser2.deserialize(bytes, serId, "").get
      }
    }
  }

  override def afterTermination(): Unit = {
    TestKit.shutdownActorSystem(noJavaSerializationSystem)
    TestKit.shutdownActorSystem(addedJavaSerializationViaSettingsSystem)
  }

}
