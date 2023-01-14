/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.journal.leveldb

import org.apache.pekko
import pekko.actor.Props
import pekko.persistence.PersistentActor

object TestActor {
  def props(persistenceId: String): Props =
    Props(new TestActor(persistenceId))

  case class DeleteCmd(toSeqNr: Long = Long.MaxValue)
}

class TestActor(override val persistenceId: String) extends PersistentActor {

  import TestActor.DeleteCmd

  val receiveRecover: Receive = {
    case _: String =>
  }

  val receiveCommand: Receive = {
    case DeleteCmd(toSeqNr) =>
      deleteMessages(toSeqNr)
      sender() ! s"$toSeqNr-deleted"

    case cmd: String =>
      persist(cmd) { evt =>
        sender() ! s"$evt-done"
      }
  }

}
