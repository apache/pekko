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

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.cluster.sharding.typed.ShardingEnvelope
import pekko.cluster.sharding.typed.ShardingMessageExtractor

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object ShardedDaemonProcessId {

  /*
   * The entity id used for sharded daemon process has the format: [revision]|[total-count]|[this-process-id]
   *
   * Pingers repeatedly ping each worker id for a given number of processes to keep them alive. When rescaling
   * to a new number of workers the revision is increased so that we can detect pings for old processes and avoid
   * starting old workers anew.
   */
  val Separator = '|'

  def decodeEntityId(id: String, initialNumberOfProcesses: Int) =
    id.split(Separator) match {
      case Array(rev, count, n) => DecodedId(rev.toLong, count.toInt, n.toInt)
      case Array(n)             =>
        DecodedId(0L, initialNumberOfProcesses, n.toInt) // ping from old/supportsRescale=false node during rolling upgrade
      case _ => throw new IllegalArgumentException(s"Unexpected id for sharded daemon process: '$id'")
    }

  final case class DecodedId(revision: Long, totalCount: Int, processNumber: Int) {
    def encodeEntityId(): String =
      if (revision == 0L) {
        // to safely do rolling updates, we encode revision 0 using the old static scheme
        processNumber.toString
      } else s"$revision$Separator$totalCount$Separator$processNumber"
  }

  final class MessageExtractor[T] extends ShardingMessageExtractor[ShardingEnvelope[T], T] {
    def entityId(message: ShardingEnvelope[T]): String = message match {
      case ShardingEnvelope(id, _) => id
    }

    // use process n for shard id
    def shardId(entityId: String): String =
      entityId.split(Separator) match {
        case Array(_, _, id) => id
        case Array(id)       => id // ping from old/supportsRescale=false node during rolling upgrade
        case id              => throw new IllegalArgumentException(s"Unexpected id for sharded daemon process: '$id'")
      }

    def unwrapMessage(message: ShardingEnvelope[T]): T = message.message
  }

  def sortedIdentitiesFor(revision: Long, numberOfProcesses: Int): Vector[String] = (0 until numberOfProcesses).map(n =>
    DecodedId(revision, numberOfProcesses, n).encodeEntityId()).toVector.sorted

  def allShardsFor(revision: Long, numberOfProcesses: Int): Set[String] = {
    val messageExtractor = new MessageExtractor[Unit]()
    sortedIdentitiesFor(revision, numberOfProcesses).map(messageExtractor.shardId).toSet
  }

}
