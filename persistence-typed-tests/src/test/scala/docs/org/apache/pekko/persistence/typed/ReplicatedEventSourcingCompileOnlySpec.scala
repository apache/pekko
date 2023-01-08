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

package docs.org.apache.pekko.persistence.typed

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.persistence.typed.ReplicaId
import pekko.persistence.typed.ReplicationId
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.persistence.typed.scaladsl.ReplicatedEventSourcing

@nowarn("msg=never used")
object ReplicatedEventSourcingCompileOnlySpec {

  // #replicas
  val DCA = ReplicaId("DC-A")
  val DCB = ReplicaId("DC-B")
  val AllReplicas = Set(DCA, DCB)
  // #replicas

  val queryPluginId = ""

  trait Command
  trait State
  trait Event

  object Shared {
    // #factory-shared
    def apply(
        system: ActorSystem[_],
        entityId: String,
        replicaId: ReplicaId): EventSourcedBehavior[Command, State, Event] = {
      ReplicatedEventSourcing.commonJournalConfig(
        ReplicationId("MyReplicatedEntity", entityId, replicaId),
        AllReplicas,
        queryPluginId) { replicationContext =>
        EventSourcedBehavior[Command, State, Event](???, ???, ???, ???)
      }
    }
    // #factory-shared
  }

  object PerReplica {
    // #factory
    def apply(
        system: ActorSystem[_],
        entityId: String,
        replicaId: ReplicaId): EventSourcedBehavior[Command, State, Event] = {
      ReplicatedEventSourcing.perReplicaJournalConfig(
        ReplicationId("MyReplicatedEntity", entityId, replicaId),
        Map(DCA -> "journalForDCA", DCB -> "journalForDCB")) { replicationContext =>
        EventSourcedBehavior[Command, State, Event](???, ???, ???, ???)
      }
    }

    // #factory
  }

}
