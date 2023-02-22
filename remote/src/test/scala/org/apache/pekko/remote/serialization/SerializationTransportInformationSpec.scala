/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.serialization

import java.nio.charset.StandardCharsets
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.actor.ActorIdentity
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.ExtendedActorSystem
import pekko.actor.Identify
import pekko.actor.RootActorPath
import pekko.remote.RARP
import pekko.serialization.Serialization
import pekko.serialization.SerializerWithStringManifest
import pekko.testkit.PekkoSpec
import pekko.testkit.ImplicitSender
import pekko.testkit.JavaSerializable
import pekko.testkit.TestActors

import java.io.NotSerializableException

object SerializationTransportInformationSpec {

  final case class TestMessage(from: ActorRef, to: ActorRef)
  final case class JavaSerTestMessage(from: ActorRef, to: ActorRef) extends JavaSerializable

  class TestSerializer(system: ExtendedActorSystem) extends SerializerWithStringManifest {
    def identifier: Int = 666
    def manifest(o: AnyRef): String = o match {
      case _: TestMessage => "A"
      case _              => throw new NotSerializableException()
    }
    def toBinary(o: AnyRef): Array[Byte] = o match {
      case TestMessage(from, to) =>
        verifyTransportInfo()
        val fromStr = Serialization.serializedActorPath(from)
        val toStr = Serialization.serializedActorPath(to)
        s"$fromStr,$toStr".getBytes(StandardCharsets.UTF_8)
      case _ => throw new NotSerializableException()
    }
    def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
      verifyTransportInfo()
      manifest match {
        case "A" =>
          val parts = new String(bytes, StandardCharsets.UTF_8).split(',')
          val fromStr = parts(0)
          val toStr = parts(1)
          val from = system.provider.resolveActorRef(fromStr)
          val to = system.provider.resolveActorRef(toStr)
          TestMessage(from, to)
        case _ => throw new NotSerializableException()
      }
    }

    private def verifyTransportInfo(): Unit = {
      Serialization.currentTransportInformation.value match {
        case null =>
          throw new IllegalStateException("currentTransportInformation was not set")
        case t =>
          if (t.system ne system)
            throw new IllegalStateException(s"wrong system in currentTransportInformation, ${t.system} != $system")
          if (t.address != system.provider.getDefaultAddress)
            throw new IllegalStateException(
              s"wrong address in currentTransportInformation, ${t.address} != ${system.provider.getDefaultAddress}")
      }
    }
  }
}

abstract class AbstractSerializationTransportInformationSpec(config: Config)
    extends PekkoSpec(config.withFallback(
      ConfigFactory.parseString("""
    pekko {
      loglevel = info
      actor {
        provider = remote
        warn-about-java-serializer-usage = off
        serializers {
          test = "org.apache.pekko.remote.serialization.SerializationTransportInformationSpec$TestSerializer"
        }
        serialization-bindings {
          "org.apache.pekko.remote.serialization.SerializationTransportInformationSpec$TestMessage" = test
        }
      }
    }
  """)))
    with ImplicitSender {

  import SerializationTransportInformationSpec._

  val port = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress.port.get
  val sysName = system.name
  val protocol =
    if (RARP(system).provider.remoteSettings.Artery.Enabled) "pekko"
    else "pekko.tcp"

  val system2 = ActorSystem(system.name, system.settings.config)
  val system2Address = RARP(system2).provider.getDefaultAddress

  "Serialization of ActorRef in remote message" must {

    "resolve address" in {
      system2.actorOf(TestActors.echoActorProps, "echo")

      val echoSel = system.actorSelection(RootActorPath(system2Address) / "user" / "echo")
      echoSel ! Identify(1)
      val echo = expectMsgType[ActorIdentity].ref.get

      echo ! TestMessage(testActor, echo)
      expectMsg(TestMessage(testActor, echo))

      echo ! JavaSerTestMessage(testActor, echo)
      expectMsg(JavaSerTestMessage(testActor, echo))

      echo ! testActor
      expectMsg(testActor)

      echo ! echo
      expectMsg(echo)

    }
  }

  override def afterTermination(): Unit = {
    shutdown(system2)
  }
}

class SerializationTransportInformationSpec
    extends AbstractSerializationTransportInformationSpec(ConfigFactory.parseString("""
  pekko.remote.artery.enabled = off
  pekko.remote.classic.netty.tcp {
    hostname = localhost
    port = 0
  }
"""))
