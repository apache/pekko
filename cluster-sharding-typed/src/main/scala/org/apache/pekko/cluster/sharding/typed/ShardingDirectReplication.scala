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

package org.apache.pekko.cluster.sharding.typed

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.eventstream.EventStream
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.annotation.DoNotInherit
import pekko.annotation.InternalApi
import pekko.persistence.typed.PublishedEvent
import pekko.persistence.typed.ReplicaId
import pekko.persistence.typed.ReplicationId

/**
 * INTERNAL API
 *
 * Used when sharding Replicated Event Sourced entities in multiple instances of sharding, for example one per DC in a Multi DC
 * Akka Cluster.
 *
 * This actor should be started once on each node where Replicated Event Sourced entities will run (the same nodes that you start
 * sharding on). The entities should be set up with [[pekko.persistence.typed.scaladsl.EventSourcedBehavior.withEventPublishing]]
 * or [[pekko.persistence.typed.javadsl.ReplicatedEventSourcedBehavior#withEventPublishing()]]
 * If using [[ReplicatedSharding]] the replication can be enabled through [[ReplicatedEntityProvider.withDirectReplication]]
 * instead of starting this actor manually.
 *
 * Subscribes to locally written events through the event stream and sends the seen events to all the sharded replicas
 * which can then fast forward their cross-replica event streams to improve latency while allowing less frequent poll
 * for the cross replica queries. Note that since message delivery is at-most-once this can not be the only
 * channel for replica events - the entities must still tail events from the journals of other replicas.
 *
 * The events are forwarded as [[pekko.cluster.sharding.typed.ShardingEnvelope]] this will work out of the box both
 * by default and with a custom extractor since the envelopes are handled internally.
 */
@InternalApi
private[pekko] object ShardingDirectReplication {

  /**
   * Not for user extension
   */
  @DoNotInherit
  sealed trait Command

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] case class VerifyStarted(replyTo: ActorRef[Done]) extends Command

  private final case class WrappedPublishedEvent(publishedEvent: PublishedEvent) extends Command

  def apply[T](
      typeName: String,
      selfReplica: Option[ReplicaId],
      replicaShardingProxies: Map[ReplicaId, ActorRef[T]]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.log.debug(
        "Subscribing to event stream to forward events to [{}] sharded replicas",
        replicaShardingProxies.size - 1)
      val publishedEventAdapter = context.messageAdapter[PublishedEvent](WrappedPublishedEvent.apply)
      context.system.eventStream ! EventStream.Subscribe[PublishedEvent](publishedEventAdapter)

      Behaviors.receiveMessage {
        case WrappedPublishedEvent(event) =>
          if (ReplicationId.isReplicationId(event.persistenceId.id)) {
            val replicationId = ReplicationId.fromString(event.persistenceId.id)
            if (replicationId.typeName == typeName) {
              context.log.traceN(
                "Forwarding event for persistence id [{}] sequence nr [{}] to replicas.",
                event.persistenceId,
                event.sequenceNumber)
              replicaShardingProxies.foreach {
                case (replica, proxy) =>
                  val newId = replicationId.withReplica(replica)
                  // receiving side is responsible for any tagging, so drop/unwrap any tags added by the local tagger
                  val withoutTags = event.withoutTags
                  val envelopedEvent = ShardingEnvelope(newId.persistenceId.id, withoutTags)
                  if (!selfReplica.contains(replica)) {
                    proxy.asInstanceOf[ActorRef[ShardingEnvelope[PublishedEvent]]] ! envelopedEvent
                  }
              }
            } else {
              context.log.traceN(
                "Not forwarding event for persistence id [{}] to replicas (wrong type name, expected [{}]).",
                event.persistenceId,
                typeName)
            }
          }
          Behaviors.same
        case VerifyStarted(replyTo) =>
          replyTo ! Done
          Behaviors.same
      }
    }

}
