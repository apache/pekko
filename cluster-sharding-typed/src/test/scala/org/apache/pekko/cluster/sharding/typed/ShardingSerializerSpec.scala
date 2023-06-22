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

package org.apache.pekko.cluster.sharding.typed

import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.internal.adapter.ActorSystemAdapter
import pekko.cluster.sharding.typed.internal.ShardingSerializer
import pekko.serialization.SerializationExtension

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ShardingSerializerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "The typed ShardingSerializer" must {

    val serialization = SerializationExtension(ActorSystemAdapter.toClassic(system))

    def checkSerialization(obj: AnyRef): Unit = {
      serialization.findSerializerFor(obj) match {
        case serializer: ShardingSerializer =>
          val blob = serializer.toBinary(obj)
          val ref = serializer.fromBinary(blob, serializer.manifest(obj))
          ref should ===(obj)

          val buffer = ByteBuffer.allocate(128)
          buffer.order(ByteOrder.LITTLE_ENDIAN)
          serializer.toBinary(obj, buffer)
          buffer.flip()
          val refFromBuf = serializer.fromBinary(buffer, serializer.manifest(obj))
          refFromBuf should ===(obj)
          buffer.clear()

        case s =>
          throw new IllegalStateException(s"Wrong serializer ${s.getClass} for ${obj.getClass}")
      }
    }

    "must serialize and deserialize ShardingEnvelope" in {
      checkSerialization(ShardingEnvelope("abc", 42))
    }

    "must serialize and deserialize StartEntity" in {
      checkSerialization(scaladsl.StartEntity[Int]("abc"))
      checkSerialization(javadsl.StartEntity.create(classOf[java.lang.Integer], "def"))
    }
  }
}
