/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.state.javadsl

import java.util.Optional
import java.util.concurrent.{ CompletableFuture, CompletionStage }

import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.{ Done, NotUsed }
import pekko.japi.Pair
import pekko.persistence.query.DurableStateChange
import pekko.persistence.query.Offset
import pekko.persistence.query.javadsl.{ DurableStateStorePagedPersistenceIdsQuery, DurableStateStoreQuery }
import pekko.persistence.query.typed.javadsl.DurableStateStoreBySliceQuery
import pekko.persistence.state.javadsl.DurableStateUpdateStore
import pekko.persistence.state.javadsl.GetObjectResult
import pekko.persistence.testkit.state.scaladsl.{ PersistenceTestKitDurableStateStore => SStore }
import pekko.stream.javadsl.Source

object PersistenceTestKitDurableStateStore {
  val Identifier = pekko.persistence.testkit.state.scaladsl.PersistenceTestKitDurableStateStore.Identifier
}

class PersistenceTestKitDurableStateStore[A](stateStore: SStore[A])
    extends DurableStateUpdateStore[A]
    with DurableStateStoreQuery[A]
    with DurableStateStoreBySliceQuery[A]
    with DurableStateStorePagedPersistenceIdsQuery[A] {

  def getObject(persistenceId: String): CompletionStage[GetObjectResult[A]] =
    stateStore.getObject(persistenceId).map(_.toJava)(stateStore.system.dispatcher).asJava

  def upsertObject(persistenceId: String, seqNr: Long, value: A, tag: String): CompletionStage[Done] =
    stateStore.upsertObject(persistenceId, seqNr, value, tag).asJava

  def deleteObject(persistenceId: String): CompletionStage[Done] = CompletableFuture.completedFuture(Done)

  def deleteObject(persistenceId: String, revision: Long): CompletionStage[Done] =
    stateStore.deleteObject(persistenceId, revision).asJava

  def changes(tag: String, offset: Offset): Source[DurableStateChange[A], pekko.NotUsed] = {
    stateStore.changes(tag, offset).asJava
  }
  def currentChanges(tag: String, offset: Offset): Source[DurableStateChange[A], pekko.NotUsed] = {
    stateStore.currentChanges(tag, offset).asJava
  }

  override def currentChangesBySlices(
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      offset: Offset): Source[DurableStateChange[A], NotUsed] =
    stateStore.currentChangesBySlices(entityType, minSlice, maxSlice, offset).asJava

  override def changesBySlices(
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      offset: Offset): Source[DurableStateChange[A], NotUsed] =
    stateStore.changesBySlices(entityType, minSlice, maxSlice, offset).asJava

  override def sliceForPersistenceId(persistenceId: String): Int =
    stateStore.sliceForPersistenceId(persistenceId)

  override def sliceRanges(numberOfRanges: Int): java.util.List[Pair[Integer, Integer]] = {
    import scala.jdk.CollectionConverters._
    stateStore
      .sliceRanges(numberOfRanges)
      .map(range => Pair(Integer.valueOf(range.min), Integer.valueOf(range.max)))
      .asJava
  }

  override def currentPersistenceIds(afterId: Optional[String], limit: Long): Source[String, NotUsed] =
    stateStore.currentPersistenceIds(afterId.toScala, limit).asJava

}
