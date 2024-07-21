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

package org.apache.pekko.stream.impl

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.Attributes.SourceLocation
import pekko.stream.scaladsl.Flow
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.GraphStageWithMaterializedValue
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler

/** Internal Api */
@InternalApi private[stream] final class SetupFlowStage[T, U, M](factory: (Materializer, Attributes) => Flow[T, U, M])
    extends GraphStageWithMaterializedValue[FlowShape[T, U], Future[M]] {

  private val in = Inlet[T]("SetupFlowStage.in")
  private val out = Outlet[U]("SetupFlowStage.out")
  override val shape = FlowShape(in, out)

  override protected def initialAttributes: Attributes = Attributes.name("setup") and SourceLocation.forLambda(factory)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[M]) = {
    val matPromise = Promise[M]()
    (createStageLogic(matPromise), matPromise.future)
  }

  private def createStageLogic(matPromise: Promise[M]) = new GraphStageLogic(shape) {
    import SetupStage._

    val subInlet = new SubSinkInlet[U]("SetupFlowStage")
    val subOutlet = new SubSourceOutlet[T]("SetupFlowStage")

    subInlet.setHandler(delegateToOutlet(push(out, _: U), () => complete(out), fail(out, _), subInlet))
    subOutlet.setHandler(delegateToInlet(() => pull(in), cause => cancel(in, cause)))

    setHandler(in, delegateToSubOutlet(() => grab(in), subOutlet))
    setHandler(out, delegateToSubInlet(subInlet))

    override def preStart(): Unit =
      try {
        val flow = factory(materializer, attributes)

        val mat = subFusingMaterializer.materialize(
          Source.fromGraph(subOutlet.source).viaMat(flow)(Keep.right).to(Sink.fromGraph(subInlet.sink)),
          attributes)
        matPromise.success(mat)
      } catch {
        case NonFatal(ex) =>
          matPromise.failure(ex)
          throw ex
      }
  }
}

/** Internal Api */
@InternalApi private[stream] final class SetupSourceStage[T, M](factory: (Materializer, Attributes) => Source[T, M])
    extends GraphStageWithMaterializedValue[SourceShape[T], Future[M]] {

  private val out = Outlet[T]("SetupSourceStage.out")
  override val shape = SourceShape(out)

  override protected def initialAttributes: Attributes = Attributes.name("setup") and SourceLocation.forLambda(factory)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[M]) = {
    val matPromise = Promise[M]()
    (createStageLogic(matPromise), matPromise.future)
  }

  private def createStageLogic(matPromise: Promise[M]) = new GraphStageLogic(shape) {
    import SetupStage._

    val subInlet = new SubSinkInlet[T]("SetupSourceStage")
    subInlet.setHandler(delegateToOutlet(push(out, _: T), () => complete(out), fail(out, _), subInlet))
    setHandler(out, delegateToSubInlet(subInlet))

    override def preStart(): Unit =
      try {
        val source = factory(materializer, attributes)

        val mat = subFusingMaterializer.materialize(source.to(Sink.fromGraph(subInlet.sink)), attributes)
        matPromise.success(mat)
      } catch {
        case NonFatal(ex) =>
          matPromise.failure(ex)
          throw ex
      }
  }
}

private object SetupStage {
  def delegateToSubOutlet[T](grab: () => T, subOutlet: GraphStageLogic#SubSourceOutlet[T]) = new InHandler {
    override def onPush(): Unit =
      subOutlet.push(grab())
    override def onUpstreamFinish(): Unit =
      subOutlet.complete()
    override def onUpstreamFailure(ex: Throwable): Unit =
      subOutlet.fail(ex)
  }

  def delegateToOutlet[T](
      push: T => Unit,
      complete: () => Unit,
      fail: Throwable => Unit,
      subInlet: GraphStageLogic#SubSinkInlet[T]) = new InHandler {
    override def onPush(): Unit =
      push(subInlet.grab())
    override def onUpstreamFinish(): Unit =
      complete()
    override def onUpstreamFailure(ex: Throwable): Unit =
      fail(ex)
  }

  def delegateToSubInlet[T](subInlet: GraphStageLogic#SubSinkInlet[T]) = new OutHandler {
    override def onPull(): Unit =
      subInlet.pull()
    override def onDownstreamFinish(cause: Throwable): Unit =
      subInlet.cancel(cause)
  }

  def delegateToInlet(pull: () => Unit, cancel: (Throwable) => Unit) = new OutHandler {
    override def onPull(): Unit =
      pull()
    override def onDownstreamFinish(cause: Throwable): Unit =
      cancel(cause)
  }
}
