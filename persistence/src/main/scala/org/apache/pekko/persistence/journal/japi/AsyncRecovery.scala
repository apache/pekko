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

import java.util.function.Consumer

import scala.concurrent.Future

import org.apache.pekko
import pekko.actor.Actor
import pekko.dispatch.ExecutionContexts
import pekko.persistence.PersistentRepr
import pekko.persistence.journal.{ AsyncRecovery => SAsyncReplay }
import pekko.util.ConstantFun.scalaAnyToUnit
import pekko.util.FutureConverters._

/**
 * Java API: asynchronous message replay and sequence number recovery interface.
 */
abstract class AsyncRecovery extends SAsyncReplay with AsyncRecoveryPlugin { this: Actor =>

  final def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
      replayCallback: (PersistentRepr) => Unit) =
    doAsyncReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max,
      new Consumer[PersistentRepr] {
        def accept(p: PersistentRepr) = replayCallback(p)
      }).asScala.map(scalaAnyToUnit)(ExecutionContexts.parasitic)

  final def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    doAsyncReadHighestSequenceNr(persistenceId, fromSequenceNr: Long)
      .asScala
      .map(_.longValue)(ExecutionContexts.parasitic)
}
