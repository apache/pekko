/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import scala.annotation.tailrec
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage._

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class UnfoldResourceSource[T, S](
    create: () => S,
    readData: (S) => Option[T],
    close: (S) => Unit)
    extends GraphStage[SourceShape[T]] {
  val out = Outlet[T]("UnfoldResourceSource.out")
  override val shape = SourceShape(out)
  override def initialAttributes: Attributes = DefaultAttributes.unfoldResourceSource

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with OutHandler {
    lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
    var open = false
    var blockingStream: S = _
    setHandler(out, this)

    override def preStart(): Unit = {
      blockingStream = create()
      open = true
    }

    @tailrec
    final override def onPull(): Unit = {
      var resumingMode = false
      try {
        readData(blockingStream) match {
          case Some(data) => push(out, data)
          case None       => closeStage()
        }
      } catch {
        case NonFatal(ex) =>
          decider(ex) match {
            case Supervision.Stop =>
              open = false
              close(blockingStream)
              failStage(ex)
            case Supervision.Restart =>
              restartState()
              resumingMode = true
            case Supervision.Resume =>
              resumingMode = true
          }
      }
      if (resumingMode) onPull()
    }

    override def onDownstreamFinish(cause: Throwable): Unit = closeStage()

    private def restartState(): Unit = {
      open = false
      close(blockingStream)
      blockingStream = create()
      open = true
    }

    private def closeStage(): Unit =
      try {
        close(blockingStream)
        open = false
        completeStage()
      } catch {
        case NonFatal(ex) => failStage(ex)
      }

    override def postStop(): Unit = {
      if (open) close(blockingStream)
    }

  }
  override def toString = "UnfoldResourceSource"
}
