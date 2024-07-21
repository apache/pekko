/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.journal.leveldb

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.annotation.InternalApi
import pekko.persistence.Persistence
import pekko.persistence.journal.leveldb.LeveldbJournal
import pekko.stream.Attributes
import pekko.stream.Materializer
import pekko.stream.Outlet
import pekko.stream.SourceShape
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.OutHandler
import pekko.stream.stage.TimerGraphStageLogicWithLogging

/**
 * INTERNAL API
 */
@InternalApi
final private[pekko] class AllPersistenceIdsStage(liveQuery: Boolean, writeJournalPluginId: String, mat: Materializer)
    extends GraphStage[SourceShape[String]] {

  val out: Outlet[String] = Outlet("AllPersistenceIds.out")

  override def shape: SourceShape[String] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogicWithLogging(shape) with OutHandler with Buffer[String] {
      override def doPush(out: Outlet[String], elem: String): Unit = super.push(out, elem)

      setHandler(out, this)
      val journal: ActorRef = Persistence(mat.system).journalFor(writeJournalPluginId)
      var initialResponseReceived = false

      override protected def logSource: Class[_] = classOf[AllPersistenceIdsStage]

      override def preStart(): Unit =
        journal.tell(LeveldbJournal.SubscribeAllPersistenceIds, getStageActor(journalInteraction).ref)

      private def journalInteraction(in: (ActorRef, Any)): Unit = {
        val (_, msg) = in
        msg match {
          case LeveldbJournal.CurrentPersistenceIds(allPersistenceIds) =>
            buffer(allPersistenceIds)
            deliverBuf(out)
            initialResponseReceived = true
            if (!liveQuery && bufferEmpty)
              completeStage()

          case LeveldbJournal.PersistenceIdAdded(persistenceId) =>
            if (liveQuery) {
              buffer(persistenceId)
              deliverBuf(out)
            }

          case _ => throw new RuntimeException() // compiler exhaustiveness check pleaser
        }
      }

      override def onPull(): Unit = {
        deliverBuf(out)
        if (initialResponseReceived && !liveQuery && bufferEmpty)
          completeStage()
      }

    }
}
