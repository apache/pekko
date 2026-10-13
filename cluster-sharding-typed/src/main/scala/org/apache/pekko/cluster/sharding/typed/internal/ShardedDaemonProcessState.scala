/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.internal

import java.time.Instant

import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.annotation.InternalApi
import pekko.cluster.ddata.Key
import pekko.cluster.ddata.ReplicatedData
import pekko.cluster.ddata.typed.scaladsl.DistributedData
import pekko.cluster.ddata.typed.scaladsl.Replicator
import pekko.cluster.sharding.typed.ShardedDaemonProcessContext

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final case class ShardedDaemonProcessState(
    revision: Long,
    numberOfProcesses: Int,
    completed: Boolean,
    started: Instant)
    extends ReplicatedData
    with ClusterShardingTypedSerializable {
  type T = ShardedDaemonProcessState

  override def merge(that: ShardedDaemonProcessState): ShardedDaemonProcessState =
    if (this.revision == that.revision) {
      if (this.completed) this
      else that
    } else if (this.revision > that.revision)
      this
    else that

  def startScalingTo(newNumberOfProcesses: Int): ShardedDaemonProcessState =
    copy(revision = revision + 1L, completed = false, numberOfProcesses = newNumberOfProcesses, started = Instant.now())
  def completeScaling(): ShardedDaemonProcessState = copy(completed = true)

}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final case class ShardedDaemonProcessStateKey(_id: String)
    extends Key[ShardedDaemonProcessState](_id)
    with ClusterShardingTypedSerializable {
  override def withId(newId: Key.KeyId): ShardedDaemonProcessStateKey =
    ShardedDaemonProcessStateKey(newId)
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object ShardedDaemonProcessState {

  val startRevision = 0L

  def initialState(initialNumberOfProcesses: Int) =
    ShardedDaemonProcessState(
      revision = ShardedDaemonProcessState.startRevision,
      numberOfProcesses = initialNumberOfProcesses,
      completed = true,
      // not quite correct but also not important, only informational
      started = Instant.now())

  def verifyRevisionBeforeStarting[T](
      behaviorFactory: ShardedDaemonProcessContext => Behavior[T]): ShardedDaemonProcessContext => Behavior[T] = {
    sdpContext =>
      Behaviors.setup { context =>
        val revision = sdpContext.revision

        if (revision == -1) {
          context.log.debug2(
            "{}: Ping from old non-rescaling node during rolling upgrade, not starting worker [{}]",
            sdpContext.name,
            sdpContext.processNumber)
          Behaviors.stopped
        } else {
          val key = ShardedDaemonProcessStateKey(sdpContext.name)
          context.log.debug2(
            "{}: Deferred start of worker to verify its revision [{}] is the latest",
            sdpContext.name,
            revision)

          // we can't anyway turn reply into T so no need for the usual adapter
          val distributedData = DistributedData(context.system)
          distributedData.replicator ! Replicator.Get(key, Replicator.ReadLocal, context.self.unsafeUpcast)
          Behaviors.receiveMessagePartial {
            case reply @ Replicator.GetSuccess(`key`) =>
              val state = reply.get(key)
              if (state.revision == revision) {
                context.log.infoN(
                  "{}: Starting Sharded Daemon Process [{}] out of a total [{}] (revision [{}])",
                  sdpContext.name,
                  sdpContext.processNumber,
                  sdpContext.totalProcesses,
                  revision)
                behaviorFactory(sdpContext).unsafeCast
              } else {
                context.log.warnN(
                  "{}: Tried to start an old revision of worker ([{}] but latest revision is [{}], started at {})",
                  sdpContext.name,
                  sdpContext.revision,
                  state.revision,
                  state.started)
                Behaviors.stopped
              }
            case Replicator.NotFound(`key`) =>
              if (revision == startRevision) {
                // No state yet but initial revision, safe
                context.log.infoN(
                  "{}: Starting Sharded Daemon Process [{}] out of a total [{}] (revision [{}] and no state found)",
                  sdpContext.name,
                  sdpContext.processNumber,
                  sdpContext.totalProcesses,
                  revision)
                behaviorFactory(sdpContext).unsafeCast
              } else {
                context.log.error2(
                  "{}: Tried to start revision [{}] of worker but no ddata state found",
                  sdpContext.name,
                  sdpContext.revision)
                Behaviors.stopped
              }
          }
        }

      }
  }

}
