/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.persistence.query.EventEnvelope
import pekko.stream.Attributes
import pekko.stream.FlowShape
import pekko.stream.Inlet
import pekko.stream.Outlet
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.GraphStageWithMaterializedValue
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] trait ReplicationStreamControl {
  def fastForward(sequenceNumber: Long): Unit
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] class FastForwardingFilter
    extends GraphStageWithMaterializedValue[FlowShape[EventEnvelope, EventEnvelope], ReplicationStreamControl] {

  val in = Inlet[EventEnvelope]("FastForwardingFilter.in")
  val out = Outlet[EventEnvelope]("FastForwardingFilter.out")

  override val shape = FlowShape[EventEnvelope, EventEnvelope](in, out)

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes): (GraphStageLogic, ReplicationStreamControl) = {
    var replicationStreamControl: ReplicationStreamControl = null
    val logic = new GraphStageLogic(shape) with InHandler with OutHandler {
      // -1 means not currently fast forwarding
      @volatile private var fastForwardTo = -1L

      override def onPush(): Unit = {
        val eventEnvelope = grab(in)
        if (fastForwardTo == -1L)
          push(out, eventEnvelope)
        else {
          if (eventEnvelope.sequenceNr <= fastForwardTo) pull(in)
          else {
            fastForwardTo = -1L
            push(out, eventEnvelope)
          }
        }
      }
      override def onPull(): Unit = pull(in)

      replicationStreamControl = new ReplicationStreamControl {
        override def fastForward(sequenceNumber: Long): Unit = {
          require(sequenceNumber > 0) // only the stage may complete a fast forward
          fastForwardTo = sequenceNumber
        }
      }

      setHandlers(in, out, this)
    }

    (logic, replicationStreamControl)
  }

}
