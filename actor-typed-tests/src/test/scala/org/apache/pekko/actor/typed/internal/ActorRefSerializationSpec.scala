/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.scaladsl.Behaviors
import pekko.serialization.{ JavaSerializer, SerializationExtension }

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

object ActorRefSerializationSpec {
  def config = ConfigFactory.parseString("""
      pekko.actor {
        # test is verifying Java serialization of ActorRef
        allow-java-serialization = on
        warn-about-java-serializer-usage = off
      }
      pekko.remote.classic.netty.tcp.port = 0
      pekko.remote.artery.canonical.port = 0
    """)

  case class MessageWrappingActorRef(s: String, ref: ActorRef[Unit]) extends java.io.Serializable
}

class ActorRefSerializationSpec
    extends ScalaTestWithActorTestKit(ActorRefSerializationSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  val serialization = SerializationExtension(system)

  "ActorRef[T]" must {
    "be serialized and deserialized by MiscMessageSerializer" in {
      val obj = spawn(Behaviors.empty[Unit])
      serialization.findSerializerFor(obj) match {
        case serializer: MiscMessageSerializer =>
          val blob = serializer.toBinary(obj)
          val ref = serializer.fromBinary(blob, serializer.manifest(obj))
          ref should ===(obj)
        case s =>
          throw new IllegalStateException(s"Wrong serializer ${s.getClass} for ${obj.getClass}")
      }
    }

    "be serialized and deserialized by JavaSerializer inside another java.io.Serializable message" in {
      val ref = spawn(Behaviors.empty[Unit])
      val obj = ActorRefSerializationSpec.MessageWrappingActorRef("some message", ref)

      serialization.findSerializerFor(obj) match {
        case serializer: JavaSerializer =>
          val blob = serializer.toBinary(obj)
          val restored = serializer.fromBinary(blob, None)
          restored should ===(obj)
        case s =>
          throw new IllegalStateException(s"Wrong serializer ${s.getClass} for ${obj.getClass}")
      }
    }
  }
}
