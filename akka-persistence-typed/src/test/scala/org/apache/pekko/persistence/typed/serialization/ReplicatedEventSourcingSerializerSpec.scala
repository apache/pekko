/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.serialization

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.ReplicaId
import pekko.persistence.typed.crdt.Counter
import pekko.persistence.typed.crdt.ORSet
import pekko.persistence.typed.internal.PublishedEventImpl
import pekko.persistence.typed.internal.ReplicatedPublishedEventMetaData
import pekko.persistence.typed.internal.VersionVector
import org.scalatest.wordspec.AnyWordSpecLike

class ReplicatedEventSourcingSerializerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "ReplicatedEventSourcingSerializer" should {
    "serializer" in {
      serializationTestKit.verifySerialization(ORSet.empty(ReplicaId("R1")))
      serializationTestKit.verifySerialization(ORSet.empty(ReplicaId("R1")).add("cat"))
      serializationTestKit.verifySerialization(ORSet.empty(ReplicaId("R1")).remove("cat"))
      serializationTestKit.verifySerialization(ORSet.empty(ReplicaId("R1")).addAll(Set("one", "two")))
      serializationTestKit.verifySerialization(ORSet.empty(ReplicaId("R1")).removeAll(Set("one", "two")))

      serializationTestKit.verifySerialization(Counter.empty)
      serializationTestKit.verifySerialization(Counter.Updated(BigInt(10)))
      serializationTestKit.verifySerialization(Counter.empty.applyOperation(Counter.Updated(BigInt(12))))

      serializationTestKit.verifySerialization(VersionVector.empty)
      serializationTestKit.verifySerialization(VersionVector.empty.updated("a", 10))

      serializationTestKit.verifySerialization(
        PublishedEventImpl(
          PersistenceId.ofUniqueId("cat"),
          10,
          "payload",
          1,
          Some(new ReplicatedPublishedEventMetaData(ReplicaId("R1"), VersionVector.empty))),
        assertEquality = false)

      serializationTestKit.verifySerialization(
        PublishedEventImpl(PersistenceId.ofUniqueId("cat"), 10, "payload", 1, None),
        assertEquality = false)
    }
  }

}
