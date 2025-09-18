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

package org.apache.pekko.persistence.journal.leveldb

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.pattern.pipe
import pekko.persistence.AtomicWrite
import pekko.persistence.journal.AsyncWriteTarget

import com.typesafe.config.Config

/**
 * A LevelDB store that can be shared by multiple actor systems. The shared store must be
 * set for each actor system that uses the store via `SharedLeveldbJournal.setStore`. The
 * shared LevelDB store is for testing only.
 */
@deprecated("Use another journal implementation or the in-mem journal in combination with the journal-proxy",
  "Akka 2.6.15")
class SharedLeveldbStore(cfg: Config) extends LeveldbStore {
  import AsyncWriteTarget._
  import context.dispatcher

  def this() = this(LeveldbStore.emptyConfig)

  override def prepareConfig: Config =
    if (cfg ne LeveldbStore.emptyConfig) cfg.getConfig("store")
    else context.system.settings.config.getConfig("pekko.persistence.journal.leveldb-shared.store")

  def receive = receiveCompactionInternal.orElse {
    case WriteMessages(messages) =>
      // TODO it would be nice to DRY this with AsyncWriteJournal, but this is using
      //      AsyncWriteProxy message protocol
      val atomicWriteCount = messages.count(_.isInstanceOf[AtomicWrite])
      val prepared = Try(preparePersistentBatch(messages))
      val writeResult = (prepared match {
        case Success(prep) =>
          // in case the asyncWriteMessages throws
          try asyncWriteMessages(prep)
          catch { case NonFatal(e) => Future.failed(e) }
        case f @ Failure(_) =>
          // exception from preparePersistentBatch => rejected
          Future.successful(messages.collect { case _: AtomicWrite => f })
      }).map { results =>
        if (results.nonEmpty && results.size != atomicWriteCount)
          throw new IllegalStateException(
            "asyncWriteMessages returned invalid number of results. " +
            s"Expected [${prepared.get.size}], but got [${results.size}]")
        results
      }

      writeResult.pipeTo(sender())

    case DeleteMessagesTo(pid, tsnr) =>
      asyncDeleteMessagesTo(pid, tsnr).pipeTo(sender())

    case ReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max) =>
      // TODO it would be nice to DRY this with AsyncWriteJournal, but this is using
      //      AsyncWriteProxy message protocol
      val replyTo = sender()
      val readHighestSequenceNrFrom = math.max(0L, fromSequenceNr - 1)
      asyncReadHighestSequenceNr(persistenceId, readHighestSequenceNrFrom)
        .flatMap { highSeqNr =>
          if (highSeqNr == 0L || max == 0L)
            Future.successful(highSeqNr)
          else {
            val toSeqNr = math.min(toSequenceNr, highSeqNr)
            asyncReplayMessages(persistenceId, fromSequenceNr, toSeqNr, max) { p =>
              if (!p.deleted) // old records from Akka 2.3 may still have the deleted flag
                adaptFromJournal(p).foreach(replyTo ! _)
            }.map(_ => highSeqNr)
          }
        }
        .map { highSeqNr =>
          ReplaySuccess(highSeqNr)
        }
        .recover {
          case e => ReplayFailure(e)
        }
        .pipeTo(replyTo)
  }
}
