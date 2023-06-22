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

package org.apache.pekko.stream.impl

import scala.concurrent.Promise
import scala.util.Try

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.dispatch.ExecutionContexts
import pekko.stream._
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, OutHandler }
import pekko.util.OptionVal

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object MaybeSource
    extends GraphStageWithMaterializedValue[SourceShape[AnyRef], Promise[Option[AnyRef]]] {
  val out = Outlet[AnyRef]("MaybeSource.out")
  override val shape = SourceShape(out)

  override protected def initialAttributes = DefaultAttributes.maybeSource

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes): (GraphStageLogic, Promise[Option[AnyRef]]) = {
    import scala.util.{ Failure => ScalaFailure, Success => ScalaSuccess }
    val promise = Promise[Option[AnyRef]]()
    val logic = new GraphStageLogic(shape) with OutHandler {

      private var arrivedEarly: OptionVal[AnyRef] = OptionVal.None

      override def preStart(): Unit = {
        promise.future.value match {
          case Some(value) =>
            // already completed, shortcut
            handleCompletion(value)
          case None =>
            // callback on future completion
            promise.future.onComplete(getAsyncCallback(handleCompletion).invoke)(ExecutionContexts.parasitic)
        }
      }

      override def onPull(): Unit = arrivedEarly match {
        case OptionVal.Some(value) =>
          push(out, value)
          completeStage()
        case _ =>
      }

      private def handleCompletion(elem: Try[Option[AnyRef]]): Unit = {
        elem match {
          case ScalaSuccess(None) =>
            completeStage()
          case ScalaSuccess(Some(value)) =>
            if (isAvailable(out)) {
              push(out, value)
              completeStage()
            } else {
              arrivedEarly = OptionVal.Some(value)
            }
          case ScalaFailure(ex) =>
            failStage(ex)
        }
      }

      override def onDownstreamFinish(cause: Throwable): Unit = {
        promise.tryComplete(ScalaSuccess(None))
      }

      override def postStop(): Unit = {
        if (!promise.isCompleted)
          promise.tryFailure(new AbruptStageTerminationException(this))
      }

      setHandler(out, this)

    }
    (logic, promise)
  }

  override def toString = "MaybeSource"
}
