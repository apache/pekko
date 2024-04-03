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
import pekko.stream.Attributes.SourceLocation
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
  override def initialAttributes: Attributes =
    DefaultAttributes.unfoldResourceSource and SourceLocation.forLambda(create)

  def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with OutHandler {
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
      private var open = false
      private var resource: S = _

      override def preStart(): Unit = {
        resource = create()
        open = true
      }

      @tailrec
      final override def onPull(): Unit = {
        var resumingMode = false
        try {
          readData(resource) match {
            case Some(data) => push(out, data)
            case None       => closeStage()
          }
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop =>
                open = false
                close(resource)
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
        close(resource)
        resource = create()
        open = true
      }

      private def closeStage(): Unit =
        try {
          close(resource)
          open = false
          completeStage()
        } catch {
          case NonFatal(ex) => failStage(ex)
        }

      override def postStop(): Unit = {
        if (open) close(resource)
      }

      setHandler(out, this)
    }

  override def toString = "UnfoldResourceSource"
}
