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

package org.apache.pekko.persistence.journal.japi

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Try

import org.apache.pekko
import scala.concurrent.ExecutionContext
import pekko.persistence._
import pekko.persistence.journal.{ AsyncWriteJournal => SAsyncWriteJournal }
import pekko.util.ConstantFun.scalaAnyToUnit
import scala.jdk.FutureConverters._
import pekko.util.ccompat.JavaConverters._

/**
 * Java API: abstract journal, optimized for asynchronous, non-blocking writes.
 */
abstract class AsyncWriteJournal extends AsyncRecovery with SAsyncWriteJournal with AsyncWritePlugin {
  import SAsyncWriteJournal.successUnit

  final def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] =
    doAsyncWriteMessages(messages.asJava).asScala.map { results =>
      results.asScala.iterator
        .map { r =>
          if (r.isPresent) Failure(r.get)
          else successUnit
        }
        .to(immutable.IndexedSeq)
    }(ExecutionContext.parasitic)

  final def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    doAsyncDeleteMessagesTo(persistenceId, toSequenceNr).asScala.map(scalaAnyToUnit)(ExecutionContext.parasitic)
  }
}
