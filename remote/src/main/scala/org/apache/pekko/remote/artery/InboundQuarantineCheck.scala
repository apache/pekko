/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.actor.ActorSelectionMessage
import pekko.event.Logging
import pekko.remote.HeartbeatMessage
import pekko.remote.UniqueAddress
import pekko.stream.Attributes
import pekko.stream.FlowShape
import pekko.stream.Inlet
import pekko.stream.Outlet
import pekko.stream.stage._
import pekko.util.OptionVal

/**
 * INTERNAL API
 */
private[remote] class InboundQuarantineCheck(inboundContext: InboundContext)
    extends GraphStage[FlowShape[InboundEnvelope, InboundEnvelope]] {
  val in: Inlet[InboundEnvelope] = Inlet("InboundQuarantineCheck.in")
  val out: Outlet[InboundEnvelope] = Outlet("InboundQuarantineCheck.out")
  override val shape: FlowShape[InboundEnvelope, InboundEnvelope] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler with StageLogging {

      override protected def logSource = classOf[InboundQuarantineCheck]

      // InHandler
      override def onPush(): Unit = {
        val env = grab(in)
        env.association match {
          case OptionVal.Some(association) =>
            if (association.associationState.isQuarantined(env.originUid)) {
              if (association.associationState.quarantinedButHarmless(env.originUid)) {
                log.info(
                  "Quarantined but harmless message from [{}#{}] was dropped. " +
                  "The system is quarantined but the UID is known to be harmless.",
                  association.remoteAddress,
                  env.originUid)
              } else {
                if (log.isDebugEnabled)
                  log.debug(
                    "Dropping message [{}] from [{}#{}] because the system is quarantined",
                    Logging.messageClassName(env.message),
                    association.remoteAddress,
                    env.originUid)
                // avoid starting outbound stream for heartbeats
                if (!env.message.isInstanceOf[Quarantined] && !isHeartbeat(env.message))
                  inboundContext.sendControl(
                    association.remoteAddress,
                    Quarantined(inboundContext.localAddress, UniqueAddress(association.remoteAddress, env.originUid)))
              }
              pull(in)
            } else
              push(out, env)
          case _ =>
            // unknown, handshake not completed
            push(out, env)
        }
      }

      private def isHeartbeat(msg: Any): Boolean = msg match {
        case _: HeartbeatMessage                              => true
        case ActorSelectionMessage(_: HeartbeatMessage, _, _) => true
        case _                                                => false
      }

      // OutHandler
      override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }
}
