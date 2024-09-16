/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import java.nio.charset.StandardCharsets
import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.ExtendedActorSystem
import pekko.serialization.Serialization
import pekko.serialization.SerializerWithStringManifest

import java.io.NotSerializableException

final case class TestPayload(ref: ActorRef)

class TestSerializer(system: ExtendedActorSystem) extends SerializerWithStringManifest {
  def identifier: Int = 666
  def manifest(o: AnyRef): String = o match {
    case _: TestPayload => "A"
    case _              => throw new RuntimeException() // compiler exhaustiveness check pleaser
  }
  def toBinary(o: AnyRef): Array[Byte] = o match {
    case TestPayload(ref) =>
      verifyTransportInfo()
      val refStr = Serialization.serializedActorPath(ref)
      refStr.getBytes(StandardCharsets.UTF_8)
    case _ => throw new NotSerializableException() // compiler exhaustiveness check pleaser
  }
  def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    verifyTransportInfo()
    manifest match {
      case "A" =>
        val refStr = new String(bytes, StandardCharsets.UTF_8)
        val ref = system.provider.resolveActorRef(refStr)
        TestPayload(ref)
      case _ => throw new NotSerializableException() // compiler exhaustiveness check pleaser
    }
  }

  private def verifyTransportInfo(): Unit =
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
