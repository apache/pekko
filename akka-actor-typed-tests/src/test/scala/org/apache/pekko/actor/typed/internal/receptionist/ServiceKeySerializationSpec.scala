/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal.receptionist

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.internal.ActorRefSerializationSpec
import pekko.actor.typed.receptionist.ServiceKey
import pekko.serialization.SerializationExtension

class ServiceKeySerializationSpec
    extends ScalaTestWithActorTestKit(ActorRefSerializationSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  val serialization = SerializationExtension(system)

  "ServiceKey[T]" must {
    "be serialized and deserialized by ServiceKeySerializer" in {
      val obj = ServiceKey[Int]("testKey")
      serialization.findSerializerFor(obj) match {
        case serializer: ServiceKeySerializer =>
          val blob = serializer.toBinary(obj)
          val ref = serializer.fromBinary(blob, serializer.manifest(obj))
          ref should be(obj)
        case s =>
          throw new IllegalStateException(s"Wrong serializer ${s.getClass} for ${obj.getClass}")
      }
    }
  }
}
