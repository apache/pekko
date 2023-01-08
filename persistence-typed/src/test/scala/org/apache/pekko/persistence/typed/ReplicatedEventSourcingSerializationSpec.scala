/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.persistence.typed.crdt.Counter
import pekko.persistence.typed.crdt.ORSet
import pekko.persistence.typed.internal.ReplicatedEventMetadata
import pekko.persistence.typed.internal.ReplicatedSnapshotMetadata
import pekko.persistence.typed.internal.VersionVector
import org.scalatest.wordspec.AnyWordSpecLike

class ReplicatedEventSourcingSerializationSpec
    extends ScalaTestWithActorTestKit(ClusterSingletonPersistenceSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  "The Replicated Event Sourcing components that needs to be serializable" must {

    "be serializable" in {
      serializationTestKit.verifySerialization(
        ReplicatedEventMetadata(ReplicaId("DC-A"), 2L, VersionVector.empty.increment("DC-B"), true))

      serializationTestKit.verifySerialization(
        ReplicatedSnapshotMetadata(
          VersionVector.empty.increment("DC-B"),
          Map(ReplicaId("DC-A") -> 1L, ReplicaId("DC-B") -> 2L)))

      serializationTestKit.verifySerialization(Counter(BigInt(24)))
      serializationTestKit.verifySerialization(Counter.Updated(BigInt(1)))
      serializationTestKit.verifySerialization(ORSet(ReplicaId("DC-A")))
      serializationTestKit.verifySerialization(ORSet.AddDeltaOp(ORSet(ReplicaId("DC-A"))))
      // FIXME DeltaGroup?
    }
  }

}
