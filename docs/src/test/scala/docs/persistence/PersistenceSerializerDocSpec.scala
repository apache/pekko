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

package docs.persistence

import com.typesafe.config._

import scala.concurrent.duration._
import org.scalatest.wordspec.AnyWordSpec
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.{ SerializationExtension, Serializer }
import org.apache.pekko.testkit.TestKit

class PersistenceSerializerDocSpec extends AnyWordSpec {

  val customSerializerConfig =
    """
      //#custom-serializer-config
      pekko.actor {
        serializers {
          my-payload = "docs.persistence.MyPayloadSerializer"
          my-snapshot = "docs.persistence.MySnapshotSerializer"
        }
        serialization-bindings {
          "docs.persistence.MyPayload" = my-payload
          "docs.persistence.MySnapshot" = my-snapshot
        }
      }
      //#custom-serializer-config
    """

  val system = ActorSystem("PersistenceSerializerDocSpec", ConfigFactory.parseString(customSerializerConfig))
  try {
    SerializationExtension(system)
  } finally {
    TestKit.shutdownActorSystem(system, 10.seconds, false)
  }
}

class MyPayload
class MySnapshot

class MyPayloadSerializer extends Serializer {
  def identifier: Int = 77124
  def includeManifest: Boolean = false
  def toBinary(o: AnyRef): Array[Byte] = ???
  def fromBinary(bytes: Array[Byte], manifest: Option[Class[?]]): AnyRef = ???
}

class MySnapshotSerializer extends Serializer {
  def identifier: Int = 77125
  def includeManifest: Boolean = false
  def toBinary(o: AnyRef): Array[Byte] = ???
  def fromBinary(bytes: Array[Byte], manifest: Option[Class[?]]): AnyRef = ???
}
