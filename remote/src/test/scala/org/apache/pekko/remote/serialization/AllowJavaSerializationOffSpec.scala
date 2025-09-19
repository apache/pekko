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

package org.apache.pekko.remote.serialization

import java.util.{ BitSet => ProgrammaticJavaDummy }
import java.util.{ Date => SerializableDummy }

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.BootstrapSetup
import pekko.actor.ExtendedActorSystem
import pekko.actor.setup.ActorSystemSetup
import pekko.serialization._
import pekko.testkit.PekkoSpec
import pekko.testkit.TestKit

import com.typesafe.config.ConfigFactory

class ConfigurationDummy
class ProgrammaticDummy

object AllowJavaSerializationOffSpec {

  val dummySerializer = new FakeSerializer

  val serializationSettings = SerializationSetup { _ =>
    List(SerializerDetails("test", dummySerializer, List(classOf[ProgrammaticDummy])))
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
    "AllowJavaSerializationOffSpec" + "NoJavaSerialization",
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

class AllowJavaSerializationOffSpec
    extends PekkoSpec(ActorSystem("AllowJavaSerializationOffSpec", AllowJavaSerializationOffSpec.actorSystemSettings)) {

  import AllowJavaSerializationOffSpec._

  // This is a weird edge case, someone creating a JavaSerializer manually and using it in a system means
  // that they'd need a different actor system to be able to create it... someone MAY pick a system with
  // allow-java-serialization=on to create the SerializationSetup and use that SerializationSetup
  // in another system with allow-java-serialization=off
  val addedJavaSerializationSettings = SerializationSetup { _ =>
    List(
      SerializerDetails("test", dummySerializer, List(classOf[ProgrammaticDummy])),
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

  val dontAllowJavaSystem =
    ActorSystem(
      "addedJavaSerializationSystem",
      ActorSystemSetup(addedJavaSerializationProgramaticallyButDisabledSettings, addedJavaSerializationSettings))

  private def verifySerialization(sys: ActorSystem, obj: AnyRef): Unit = {
    val serialization = SerializationExtension(sys)
    val bytes = serialization.serialize(obj).get
    val serializer = serialization.findSerializerFor(obj)
    val manifest = Serializers.manifestFor(serializer, obj)
    serialization.deserialize(bytes, serializer.identifier, manifest).get
  }

  "Disabling java serialization" should {

    "throw if passed system to JavaSerializer has allow-java-serialization = off" in {
      intercept[DisabledJavaSerializer.JavaSerializationException] {
        new JavaSerializer(noJavaSerializationSystem.asInstanceOf[ExtendedActorSystem])
      }.getMessage should include("pekko.actor.allow-java-serialization = off")

      intercept[DisabledJavaSerializer.JavaSerializationException] {
        SerializationExtension(dontAllowJavaSystem)
          .findSerializerFor(new ProgrammaticJavaDummy)
          .toBinary(new ProgrammaticJavaDummy)
      }
    }

    "have replaced java serializer" in {
      // allow-java-serialization = on in `system`
      val serializer = SerializationExtension(system).findSerializerFor(new ProgrammaticJavaDummy)
      serializer.getClass should ===(classOf[JavaSerializer])

      // should not allow deserialization, it would have been java serialization!
      val serializer2 = SerializationExtension(dontAllowJavaSystem).findSerializerFor(new ProgrammaticJavaDummy)
      serializer2.getClass should ===(classOf[DisabledJavaSerializer])
      serializer2.identifier should ===(serializer.identifier)

      verifySerialization(system, new ProgrammaticDummy)
      verifySerialization(dontAllowJavaSystem, new ProgrammaticDummy)
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
    TestKit.shutdownActorSystem(dontAllowJavaSystem)
  }

}
