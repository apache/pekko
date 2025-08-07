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

package org.apache.pekko.stream.impl.fusing

import java.util.concurrent.TimeUnit.NANOSECONDS

import scala.annotation.nowarn
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.immutable.VectorBuilder
import scala.concurrent.Future
import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.util.{ Failure, Success, Try }
import scala.util.control.{ NoStackTrace, NonFatal }
import scala.util.control.Exception.Catcher
import org.apache.pekko
import pekko.actor.{ ActorRef, Terminated }
import pekko.annotation.InternalApi
import pekko.event._
import pekko.event.Logging.LogLevel
import pekko.stream.{ Supervision, _ }
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.Attributes.{ InputBuffer, LogLevels }
import pekko.stream.Attributes.SourceLocation
import pekko.stream.OverflowStrategies._
import pekko.stream.Supervision.Decider
import pekko.stream.impl.{
  Buffer => BufferImpl,
  ContextPropagation,
  FailedSource,
  JavaStreamSource,
  ReactiveStreamsCompliance,
  TraversalBuilder
}
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.impl.fusing.GraphStages.{ FutureSource, SimpleLinearGraphStage, SingleSource }
import pekko.stream.scaladsl.{
  DelayStrategy,
  Source,
  StatefulMapConcatAccumulator,
  StatefulMapConcatAccumulatorFactory
}
import pekko.stream.stage._
import pekko.util.{ unused, ConstantFun, OptionVal }
import pekko.util.ccompat._

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Map[In, Out](f: In => Out) extends GraphStage[FlowShape[In, Out]] {
  val in = Inlet[In]("Map.in")
  val out = Outlet[Out]("Map.out")
  override val shape = FlowShape(in, out)

  override def initialAttributes: Attributes = DefaultAttributes.map and SourceLocation.forLambda(f)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private def decider =
        inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      override def onPush(): Unit = {
        try {
          push(out, f(grab(in)))
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop => failStage(ex)
              case _                => pull(in)
            }
        }
      }

      override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Filter[T](p: T => Boolean) extends SimpleLinearGraphStage[T] {
  override def initialAttributes: Attributes = DefaultAttributes.filter and SourceLocation.forLambda(p)

  override def toString: String = "Filter"

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler with InHandler {
      def decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var buffer: OptionVal[T] = OptionVal.none
      private val contextPropagation = ContextPropagation()

      override def preStart(): Unit = pull(in)
      override def onPush(): Unit =
        try {
          val elem = grab(in)
          if (p(elem))
            if (isAvailable(out)) {
              push(out, elem)
              pull(in)
            } else {
              buffer = OptionVal.Some(elem)
              contextPropagation.suspendContext()
            }
          else pull(in)
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop => failStage(ex)
              case _                => pull(in)
            }
        }

      override def onPull(): Unit =
        buffer match {
          case OptionVal.Some(value) =>
            contextPropagation.resumeContext()
            push(out, value)
            buffer = OptionVal.none
            if (!isClosed(in)) pull(in)
            else completeStage()
          case _ => // already pulled
        }

      override def onUpstreamFinish(): Unit =
        if (buffer.isEmpty) super.onUpstreamFinish()
      // else onPull will complete

      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class TakeWhile[T](p: T => Boolean, inclusive: Boolean = false)
    extends SimpleLinearGraphStage[T] {
  override def initialAttributes: Attributes = DefaultAttributes.takeWhile and SourceLocation.forLambda(p)

  override def toString: String = "TakeWhile"

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler with InHandler {
      override def toString = "TakeWhileLogic"

      def decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      override def onPush(): Unit = {
        try {
          val elem = grab(in)
          if (p(elem)) {
            push(out, elem)
          } else {
            if (inclusive) push(out, elem)
            completeStage()
          }
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop => failStage(ex)
              case _                => pull(in)
            }
        }
      }

      override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class DropWhile[T](p: T => Boolean) extends SimpleLinearGraphStage[T] {
  override def initialAttributes: Attributes = DefaultAttributes.dropWhile and SourceLocation.forLambda(p)

  def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      override def onPush(): Unit =
        try {
          val elem = grab(in)
          if (p(elem)) {
            pull(in)
          } else {
            push(out, elem)
            setHandler(in, () => push(out, grab(in)))
          }
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop => failStage(ex)
              case _                => pull(in)
            }
        }

      override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }

  override def toString = "DropWhile"
}

private[stream] object Collect {
  // Cached function that can be used with PartialFunction.applyOrElse to ensure that A) the guard is only applied once,
  // and the caller can check the returned value with Collect.notApplied to query whether the PF was applied or not.
  // Prior art: https://github.com/scala/scala/blob/v2.11.4/src/library/scala/collection/immutable/List.scala#L458
  object NotApplied extends (Any => Any) {
    final override def apply(v1: Any): Any = this
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Collect[In, Out](pf: PartialFunction[In, Out])
    extends GraphStage[FlowShape[In, Out]] {
  val in = Inlet[In]("Collect.in")
  val out = Outlet[Out]("Collect.out")
  override val shape = FlowShape(in, out)

  override def initialAttributes: Attributes = DefaultAttributes.collect and SourceLocation.forLambda(pf)

  def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
      import Collect.NotApplied

      @nowarn("msg=Any")
      override def onPush(): Unit =
        try {
          // 1. `applyOrElse` is faster than (`pf.isDefinedAt` and then `pf.apply`)
          // 2. using reference comparing here instead of pattern matching can generate less and quicker bytecode,
          //   eg: just a simple `IF_ACMPNE`, and you can find the same trick in `CollectWhile` operator.
          //   If you interest, you can check the associated PR for this change and the
          //   current implementation of `scala.collection.IterableOnceOps.collectFirst`.
          pf.applyOrElse(grab(in), NotApplied) match {
            case _: NotApplied.type   => pull(in)
            case elem: Out @unchecked => push(out, elem)
          }
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop => failStage(ex)
              case _                =>
                // The !hasBeenPulled(in) check is not required here since it
                // isn't possible to do an additional pull(in) due to the nature
                // of how collect works
                pull(in)
            }
        }

      override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }

  override def toString = "Collect"
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Recover[T](pf: PartialFunction[Throwable, T])
    extends SimpleLinearGraphStage[T] {
  override protected def initialAttributes: Attributes = DefaultAttributes.recover and SourceLocation.forLambda(pf)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      import Collect.NotApplied

      var recovered: OptionVal[T] = OptionVal.none

      override def onPush(): Unit = push(out, grab(in))

      override def onPull(): Unit = recovered match {
        case OptionVal.Some(elem) =>
          push(out, elem)
          completeStage()
        case _ => pull(in)
      }

      @nowarn("msg=Any")
      override def onUpstreamFailure(ex: Throwable): Unit =
        try pf.applyOrElse(ex, NotApplied) match {
            case _: NotApplied.type   => failStage(ex)
            case result: T @unchecked => {
              if (isAvailable(out)) {
                push(out, result)
                completeStage()
              } else {
                recovered = OptionVal.Some(result)
              }
            }
            case _ => throw new RuntimeException() // won't happen, compiler exhaustiveness check pleaser
          }
        catch {
          case NonFatal(ex) => failStage(ex)
        }

      setHandlers(in, out, this)
    }
}

/**
 * Maps error with the provided function if it is defined for an error or, otherwise, passes it on unchanged.
 *
 * While similar to [[Recover]] this operator can be used to transform an error signal to a different one *without* logging
 * it as an error in the process. So in that sense it is NOT exactly equivalent to `recover(t => throw t2)` since recover
 * would log the `t2` error.
 */
@InternalApi private[pekko] final case class MapError[T](f: PartialFunction[Throwable, Throwable])
    extends SimpleLinearGraphStage[T] {

  override protected def initialAttributes: Attributes = DefaultAttributes.mapError

  override def createLogic(attr: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      override def onPush(): Unit = push(out, grab(in))
      import Collect.NotApplied

      @nowarn("msg=Any")
      override def onUpstreamFailure(ex: Throwable): Unit = f.applyOrElse(ex, NotApplied) match {
        case _: NotApplied.type => super.onUpstreamFailure(ex)
        case t: Throwable       => super.onUpstreamFailure(t)
        case _                  => throw new IllegalStateException() // won't happen, compiler exhaustiveness check pleaser
      }

      override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Take[T](count: Long) extends SimpleLinearGraphStage[T] {
  override def initialAttributes: Attributes = DefaultAttributes.take

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var left: Long = count

      override def onPush(): Unit = {
        if (left > 0) {
          push(out, grab(in))
          left -= 1
        }
        if (left <= 0) completeStage()
      }

      override def onPull(): Unit = {
        if (left > 0) pull(in)
        else completeStage()
      }

      setHandlers(in, out, this)
    }

  override def toString: String = "Take"
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Drop[T](count: Long) extends SimpleLinearGraphStage[T] {
  override def initialAttributes: Attributes = DefaultAttributes.drop

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var left: Long = count

      override def onPush(): Unit = {
        if (left > 0) {
          left -= 1
          pull(in)
        } else push(out, grab(in))
      }

      override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }

  override def toString: String = "Drop"
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Scan[In, Out](zero: Out, f: (Out, In) => Out)
    extends GraphStage[FlowShape[In, Out]] {
  override val shape = FlowShape[In, Out](Inlet("Scan.in"), Outlet("Scan.out"))

  override def initialAttributes: Attributes = DefaultAttributes.scan and SourceLocation.forLambda(f)

  override def toString: String = "Scan"

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler { self =>
      private var aggregator = zero
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      import Supervision.{ Restart, Resume, Stop }
      import shape.{ in, out }

      // Initial behavior makes sure that the zero gets flushed if upstream is empty
      setHandlers(
        in,
        out,
        new InHandler with OutHandler {
          override def onPush(): Unit = ()

          override def onUpstreamFinish(): Unit =
            setHandler(out,
              new OutHandler {
                override def onPull(): Unit = {
                  push(out, aggregator)
                  completeStage()
                }
              })

          override def onPull(): Unit = {
            push(out, aggregator)
            setHandlers(in, out, self)
          }
        })

      override def onPull(): Unit = pull(in)

      override def onPush(): Unit = {
        try {
          aggregator = f(aggregator, grab(in))
          push(out, aggregator)
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Resume  => if (!hasBeenPulled(in)) pull(in)
              case Stop    => failStage(ex)
              case Restart =>
                aggregator = zero
                push(out, aggregator)
            }
        }
      }
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class ScanAsync[In, Out](zero: Out, f: (Out, In) => Future[Out])
    extends GraphStage[FlowShape[In, Out]] {

  import pekko.dispatch.ExecutionContexts

  val in = Inlet[In]("ScanAsync.in")
  val out = Outlet[Out]("ScanAsync.out")
  override val shape: FlowShape[In, Out] = FlowShape[In, Out](in, out)

  override val initialAttributes: Attributes = Attributes.name("scanAsync") and SourceLocation.forLambda(f)

  override val toString: String = "ScanAsync"

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler { self =>
      private var current: Out = zero
      private var elementHandled: Boolean = false

      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private val ZeroHandler: OutHandler with InHandler = new OutHandler with InHandler {
        override def onPush(): Unit =
          throw new IllegalStateException("No push should happen before zero value has been consumed")

        override def onPull(): Unit = {
          elementHandled = true
          push(out, current)
          setHandlers(in, out, self)
        }

        override def onUpstreamFinish(): Unit =
          setHandler(out,
            new OutHandler {
              override def onPull(): Unit = {
                push(out, current)
                completeStage()
              }
            })
      }

      private def onRestart(): Unit = {
        current = zero
        elementHandled = false
      }

      private def safePull(): Unit = {
        if (isClosed(in)) {
          completeStage()
        } else if (isAvailable(out)) {
          if (!hasBeenPulled(in)) {
            tryPull(in)
          }
        }
      }

      private def pushAndPullOrFinish(update: Out): Unit = {
        push(out, update)
        safePull()
      }

      private def doSupervision(t: Throwable): Unit = {
        decider(t) match {
          case Supervision.Stop    => failStage(t)
          case Supervision.Resume  => safePull()
          case Supervision.Restart =>
            onRestart()
            safePull()
        }
        elementHandled = true
      }

      private val futureCB = getAsyncCallback[Try[Out]] {
        case Success(next) =>
          if (next != null) {
            current = next
            pushAndPullOrFinish(next)
            elementHandled = true
          } else {
            doSupervision(ReactiveStreamsCompliance.elementMustNotBeNullException)
          }
        case Failure(t) => doSupervision(t)
      }.invoke _

      setHandlers(in, out, ZeroHandler)

      def onPull(): Unit = safePull()

      def onPush(): Unit = {
        try {
          elementHandled = false

          val eventualCurrent = f(current, grab(in))

          eventualCurrent.value match {
            case Some(result) => futureCB(result)
            case _            => eventualCurrent.onComplete(futureCB)(ExecutionContexts.parasitic)
          }
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop    => failStage(ex)
              case Supervision.Restart => onRestart()
              case Supervision.Resume  => ()
            }
            tryPull(in)
            elementHandled = true
        }
      }

      override def onUpstreamFinish(): Unit = {
        if (elementHandled) {
          completeStage()
        }
      }

      override val toString: String = s"ScanAsync.Logic(completed=$elementHandled)"
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object Fold {
  def apply[In, Out](zero: Out, f: (Out, In) => Out): Fold[In, Out] = new Fold(zero, ConstantFun.anyToTrue, f)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Fold[In, Out](zero: Out,
    predicate: Out => Boolean,
    f: (Out, In) => Out)
    extends GraphStage[FlowShape[In, Out]] {

  val in = Inlet[In]("Fold.in")
  val out = Outlet[Out]("Fold.out")
  override val shape: FlowShape[In, Out] = FlowShape(in, out)

  override val initialAttributes = DefaultAttributes.fold and SourceLocation.forLambda(f)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var aggregator: Out = zero

      private def decider =
        inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      override def onPush(): Unit = {
        val elem = grab(in)
        try {
          aggregator = f(aggregator, elem)
          if (!predicate(aggregator)) {
            push(out, aggregator)
            completeStage()
          }
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop    => failStage(ex)
              case Supervision.Restart => aggregator = zero
              case _                   => ()
            }
        } finally {
          if (!isClosed(in)) pull(in)
        }
      }

      override def onPull(): Unit = {
        if (isClosed(in)) {
          push(out, aggregator)
          completeStage()
        } else {
          pull(in)
        }
      }

      override def onUpstreamFinish(): Unit = {
        if (isAvailable(out)) {
          push(out, aggregator)
          completeStage()
        }
      }

      setHandlers(in, out, this)
    }

  override def toString: String = "Fold"
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class FoldAsync[In, Out](zero: Out, f: (Out, In) => Future[Out])
    extends GraphStage[FlowShape[In, Out]] {

  import pekko.dispatch.ExecutionContexts

  val in = Inlet[In]("FoldAsync.in")
  val out = Outlet[Out]("FoldAsync.out")
  val shape = FlowShape.of(in, out)

  override def toString: String = "FoldAsync"

  override val initialAttributes = DefaultAttributes.foldAsync and SourceLocation.forLambda(f)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var aggregator: Out = zero
      private var aggregating: Future[Out] = Future.successful(aggregator)

      private def onRestart(@unused t: Throwable): Unit = {
        aggregator = zero
      }

      private val futureCB = getAsyncCallback[Try[Out]] {
        case Success(update) if update != null =>
          aggregator = update

          if (isClosed(in)) {
            push(out, update)
            completeStage()
          } else if (isAvailable(out) && !hasBeenPulled(in)) tryPull(in)

        case other =>
          val ex = other match {
            case Failure(t)    => t
            case Success(null) =>
              ReactiveStreamsCompliance.elementMustNotBeNullException
            case Success(_) =>
              throw new IllegalArgumentException() // won't happen, compiler exhaustiveness check pleaser
          }
          val supervision = decider(ex)

          if (supervision == Supervision.Stop) failStage(ex)
          else {
            if (supervision == Supervision.Restart) onRestart(ex)

            if (isClosed(in)) {
              push(out, aggregator)
              completeStage()
            } else if (isAvailable(out) && !hasBeenPulled(in)) tryPull(in)
          }
      }.invoke _

      def onPush(): Unit = {
        try {
          aggregating = f(aggregator, grab(in))
          handleAggregatingValue()
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop => failStage(ex)
              case supervision      => {
                supervision match {
                  case Supervision.Restart => onRestart(ex)
                  case _                   => () // just ignore on Resume
                }

                tryPull(in)
              }
            }
        }
      }

      override def onUpstreamFinish(): Unit = {
        handleAggregatingValue()
      }

      def onPull(): Unit = if (!hasBeenPulled(in)) tryPull(in)

      private def handleAggregatingValue(): Unit = {
        aggregating.value match {
          case Some(result) => futureCB(result) // already completed
          case _            => aggregating.onComplete(futureCB)(ExecutionContexts.parasitic)
        }
      }

      setHandlers(in, out, this)

      override def toString =
        s"FoldAsync.Logic(completed=${aggregating.isCompleted})"
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Intersperse[T](start: Option[T], inject: T, end: Option[T])
    extends SimpleLinearGraphStage[T] {
  ReactiveStreamsCompliance.requireNonNullElement(inject)
  if (start.isDefined) ReactiveStreamsCompliance.requireNonNullElement(start.get)
  if (end.isDefined) ReactiveStreamsCompliance.requireNonNullElement(end.get)

  override def createLogic(attr: Attributes): GraphStageLogic = new GraphStageLogic(shape) with OutHandler {
    val startInHandler = new InHandler {
      override def onPush(): Unit = {
        // if else (to avoid using Iterator[T].flatten in hot code)
        if (start.isDefined) emitMultiple(out, Iterator(start.get, grab(in)))
        else emit(out, grab(in))
        setHandler(in, restInHandler) // switch handler
      }

      override def onUpstreamFinish(): Unit = {
        emitMultiple(out, Iterator(start, end).flatten)
        completeStage()
      }
    }

    val restInHandler = new InHandler {
      override def onPush(): Unit = emitMultiple(out, Iterator(inject, grab(in)))

      override def onUpstreamFinish(): Unit = {
        if (end.isDefined) emit(out, end.get)
        completeStage()
      }
    }

    def onPull(): Unit = pull(in)

    setHandler(in, startInHandler)
    setHandler(out, this)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class GroupedWeighted[T](minWeight: Long, costFn: T => Long)
    extends GraphStage[FlowShape[T, immutable.Seq[T]]] {
  require(minWeight > 0, "minWeight must be greater than 0")

  val in = Inlet[T]("GroupedWeighted.in")
  val out = Outlet[immutable.Seq[T]]("GroupedWeighted.out")
  override val shape: FlowShape[T, immutable.Seq[T]] = FlowShape(in, out)

  override def initialAttributes: Attributes = DefaultAttributes.groupedWeighted

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var builder = Vector.newBuilder[T]
      private var left: Long = minWeight

      override def onPush(): Unit = {
        val elem = grab(in)
        val cost = costFn(elem)
        if (cost < 0L)
          failStage(new IllegalArgumentException(s"Negative weight [$cost] for element [$elem] is not allowed"))
        else {
          builder += elem
          left -= cost
          if (left <= 0) {
            val elements = builder.result()
            builder.clear()
            left = minWeight
            push(out, elements)
          } else {
            pull(in)
          }
        }
      }

      override def onPull(): Unit = {
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        // Since the upstream has finished we have to push any buffered elements downstream.
        val elements = builder.result()
        if (elements.nonEmpty) {
          left = minWeight
          push(out, elements)
        }
        builder = null // free for GC
        completeStage()
      }

      setHandlers(in, out, this)
    }

}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class LimitWeighted[T](val n: Long, val costFn: T => Long)
    extends SimpleLinearGraphStage[T] {
  override def initialAttributes: Attributes = DefaultAttributes.limitWeighted and SourceLocation.forLambda(costFn)

  def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var left = n

      override def onPush(): Unit =
        try {
          val elem = grab(in)
          left -= costFn(elem)
          if (left >= 0) push(out, elem) else failStage(new StreamLimitReachedException(n))
        } catch {
          case NonFatal(ex) => decider(ex) match {
              case Supervision.Stop    => failStage(ex)
              case Supervision.Resume  => if (!hasBeenPulled(in)) pull(in)
              case Supervision.Restart =>
                left = n
                if (!hasBeenPulled(in)) pull(in)
            }
        }

      override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }

  override def toString = "LimitWeighted"
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Sliding[T](val n: Int, val step: Int)
    extends GraphStage[FlowShape[T, immutable.Seq[T]]] {
  require(n > 0, "n must be greater than 0")
  require(step > 0, "step must be greater than 0")

  val in = Inlet[T]("Sliding.in")
  val out = Outlet[immutable.Seq[T]]("Sliding.out")
  override val shape: FlowShape[T, immutable.Seq[T]] = FlowShape(in, out)

  override protected val initialAttributes: Attributes = DefaultAttributes.sliding

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var buf = Vector.empty[T]

      override def onPush(): Unit = {
        buf :+= grab(in)
        if (buf.size < n) {
          pull(in)
        } else if (buf.size == n) {
          push(out, buf)
        } else if (step <= n) {
          buf = buf.drop(step)
          if (buf.size == n) {
            push(out, buf)
          } else pull(in)
        } else {
          if (buf.size == step) {
            buf = buf.drop(step)
          }
          pull(in)
        }
      }

      override def onPull(): Unit = {
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {

        // We can finish current stage directly if:
        //  1. the buf is empty or
        //  2. when the step size is greater than the sliding size (step > n) and current stage is in between
        //     two sliding (ie. buf.size >= n && buf.size < step).
        //
        // Otherwise it means there is still a not finished sliding so we have to push them before finish current stage.
        if (buf.size < n && buf.size > 0) {
          push(out, buf)
        }
        completeStage()
      }

      this.setHandlers(in, out, this)
    }

}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Buffer[T](size: Int, overflowStrategy: OverflowStrategy)
    extends SimpleLinearGraphStage[T] {

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler with StageLogging {
      override protected def logSource: Class[_] = classOf[Buffer[_]]

      private val buffer: BufferImpl[T] = BufferImpl(size, inheritedAttributes)

      private val name = inheritedAttributes.nameOrDefault(getClass.toString)
      val enqueueAction: T => Unit =
        overflowStrategy match {
          case s: DropHead =>
            elem =>
              if (buffer.isFull) {
                log.log(
                  s.logLevel,
                  "Dropping the head element because buffer is full and overflowStrategy is: [DropHead] in stream [{}]",
                  name)
                buffer.dropHead()
              }
              buffer.enqueue(elem)
              pull(in)
          case s: DropTail =>
            elem =>
              if (buffer.isFull) {
                log.log(
                  s.logLevel,
                  "Dropping the tail element because buffer is full and overflowStrategy is: [DropTail] in stream [{}]",
                  name)
                buffer.dropTail()
              }
              buffer.enqueue(elem)
              pull(in)
          case s: DropBuffer =>
            elem =>
              if (buffer.isFull) {
                log.log(
                  s.logLevel,
                  "Dropping all the buffered elements because buffer is full and overflowStrategy is: [DropBuffer] in stream [{}]",
                  name)
                buffer.clear()
              }
              buffer.enqueue(elem)
              pull(in)
          case s: DropNew =>
            elem =>
              if (!buffer.isFull) buffer.enqueue(elem)
              else
                log.log(
                  s.logLevel,
                  "Dropping the new element because buffer is full and overflowStrategy is: [DropNew] in stream [{}]",
                  name)
              pull(in)
          case s: Backpressure =>
            elem =>
              buffer.enqueue(elem)
              if (!buffer.isFull) pull(in)
              else
                log.log(
                  s.logLevel,
                  "Backpressuring because buffer is full and overflowStrategy is: [Backpressure] in stream [{}]",
                  name)
          case s: Fail =>
            elem =>
              if (buffer.isFull) {
                log.log(
                  s.logLevel,
                  "Failing because buffer is full and overflowStrategy is: [Fail] in stream [{}]",
                  name)
                failStage(BufferOverflowException(s"Buffer overflow (max capacity was: $size)!"))
              } else {
                buffer.enqueue(elem)
                pull(in)
              }
        }

      override def preStart(): Unit = {
        pull(in)
      }

      override def onPush(): Unit = {
        val elem = grab(in)
        // If out is available, then it has been pulled but no dequeued element has been delivered.
        // It means the buffer at this moment is definitely empty,
        // so we just push the current element to out, then pull.
        if (isAvailable(out)) {
          push(out, elem)
          pull(in)
        } else {
          enqueueAction(elem)
        }
      }

      override def onPull(): Unit = {
        if (buffer.nonEmpty) push(out, buffer.dequeue())
        if (isClosed(in)) {
          if (buffer.isEmpty) completeStage()
        } else if (!hasBeenPulled(in)) {
          pull(in)
        }
      }

      override def onUpstreamFinish(): Unit = {
        if (buffer.isEmpty) completeStage()
      }

      setHandlers(in, out, this)
    }

}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Batch[In, Out](
    val max: Long,
    val costFn: In => Long,
    val seed: In => Out,
    val aggregate: (Out, In) => Out)
    extends GraphStage[FlowShape[In, Out]] {

  val in = Inlet[In]("Batch.in")
  val out = Outlet[Out]("Batch.out")

  override val shape: FlowShape[In, Out] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {

      lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var agg: Out = null.asInstanceOf[Out]
      private var left: Long = max
      private var pending: In = null.asInstanceOf[In]
      private val contextPropagation = ContextPropagation()

      private def flush(): Unit = {
        if (agg != null) {
          push(out, agg)
          left = max
        }
        if (pending != null) {
          try {
            agg = seed(pending)
            left -= costFn(pending)
            pending = null.asInstanceOf[In]
          } catch {
            case NonFatal(ex) =>
              decider(ex) match {
                case Supervision.Stop    => failStage(ex)
                case Supervision.Restart => restartState()
                case Supervision.Resume  =>
                  pending = null.asInstanceOf[In]
              }
          }
        } else {
          agg = null.asInstanceOf[Out]
        }
      }

      override def preStart() = pull(in)

      def onPush(): Unit = {
        val elem = grab(in)
        val cost = costFn(elem)
        contextPropagation.suspendContext()

        if (agg == null) {
          try {
            agg = seed(elem)
            left -= cost
          } catch {
            case NonFatal(ex) =>
              decider(ex) match {
                case Supervision.Stop    => failStage(ex)
                case Supervision.Restart =>
                  restartState()
                case Supervision.Resume =>
              }
          }
        } else if (left < cost) {
          pending = elem
        } else {
          try {
            agg = aggregate(agg, elem)
            left -= cost
          } catch {
            case NonFatal(ex) =>
              decider(ex) match {
                case Supervision.Stop    => failStage(ex)
                case Supervision.Restart =>
                  restartState()
                case Supervision.Resume =>
              }
          }
        }

        if (isAvailable(out)) flush()
        if (pending == null) pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        if (agg == null) completeStage()
      }

      def onPull(): Unit = {
        if (agg == null) {
          if (isClosed(in)) completeStage()
          else if (!hasBeenPulled(in)) pull(in)
        } else if (isClosed(in)) {
          contextPropagation.resumeContext()
          push(out, agg)
          if (pending == null) completeStage()
          else {
            try {
              agg = seed(pending)
            } catch {
              case NonFatal(ex) =>
                decider(ex) match {
                  case Supervision.Stop    => failStage(ex)
                  case Supervision.Resume  =>
                  case Supervision.Restart =>
                    restartState()
                    if (!hasBeenPulled(in)) pull(in)
                }
            }
            pending = null.asInstanceOf[In]
          }
        } else {
          contextPropagation.resumeContext()
          flush()
          if (!hasBeenPulled(in)) pull(in)
        }

      }

      private def restartState(): Unit = {
        agg = null.asInstanceOf[Out]
        left = max
        pending = null.asInstanceOf[In]
      }

      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class Expand[In, Out](val extrapolate: In => Iterator[Out])
    extends GraphStage[FlowShape[In, Out]] {
  private val in = Inlet[In]("expand.in")
  private val out = Outlet[Out]("expand.out")

  override def initialAttributes = DefaultAttributes.expand and SourceLocation.forLambda(extrapolate)

  override val shape = FlowShape(in, out)

  override def createLogic(attr: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    private var iterator: Iterator[Out] = Iterator.empty
    private var expanded = false
    private val contextPropagation = ContextPropagation()

    override def preStart(): Unit = pull(in)

    def onPush(): Unit = {
      iterator = extrapolate(grab(in))
      if (iterator.hasNext) {
        contextPropagation.suspendContext()
        if (isAvailable(out)) {
          expanded = true
          pull(in)
          push(out, iterator.next())
        } else expanded = false
      } else pull(in)
    }

    override def onUpstreamFinish(): Unit = {
      if (iterator.hasNext && !expanded) () // need to wait
      else completeStage()
    }

    def onPull(): Unit = {
      if (iterator.hasNext) {
        contextPropagation.resumeContext()
        if (!expanded) {
          expanded = true
          if (isClosed(in)) {
            push(out, iterator.next())
            completeStage()
          } else {
            // expand needs to pull first to be “fair” when upstream is not actually slow
            pull(in)
            push(out, iterator.next())
          }
        } else push(out, iterator.next())
      }
    }

    setHandler(in, this)
    setHandler(out, this)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object MapAsync {

  final class Holder[T](var elem: Try[T], val cb: AsyncCallback[Holder[T]]) extends (Try[T] => Unit) {

    // To support both fail-fast when the supervision directive is Stop
    // and not calling the decider multiple times (#23888) we need to cache the decider result and re-use that
    private var cachedSupervisionDirective: OptionVal[Supervision.Directive] = OptionVal.None

    def supervisionDirectiveFor(decider: Supervision.Decider, ex: Throwable): Supervision.Directive = {
      cachedSupervisionDirective match {
        case OptionVal.Some(d) => d
        case _                 =>
          val d = decider(ex)
          cachedSupervisionDirective = OptionVal.Some(d)
          d
      }
    }

    def setElem(t: Try[T]): Unit = {
      elem = t
    }

    override def apply(t: Try[T]): Unit = {
      setElem(t)
      cb.invoke(this)
    }
  }

  val NotYetThere = Failure(new Exception with NoStackTrace)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class MapAsync[In, Out](parallelism: Int, f: In => Future[Out])
    extends GraphStage[FlowShape[In, Out]] {
  require(parallelism >= 1, "parallelism must be at least 1")

  import MapAsync._

  private val in = Inlet[In]("MapAsync.in")
  private val out = Outlet[Out]("MapAsync.out")

  override def initialAttributes = DefaultAttributes.mapAsync and SourceLocation.forLambda(f)

  override val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {

      lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
      var buffer: BufferImpl[Holder[Out]] = _

      private val futureCB = getAsyncCallback[Holder[Out]](holder =>
        holder.elem match {
          case Success(_)  => pushNextIfPossible()
          case Failure(ex) =>
            holder.supervisionDirectiveFor(decider, ex) match {
              // fail fast as if supervision says so
              case Supervision.Stop => failStage(ex)
              case _                => pushNextIfPossible()
            }
        })

      override def preStart(): Unit = buffer = BufferImpl(parallelism, inheritedAttributes)

      override def onPull(): Unit = pushNextIfPossible()

      override def onPush(): Unit = {
        try {
          val future = f(grab(in))
          val holder = new Holder[Out](NotYetThere, futureCB)
          buffer.enqueue(holder)

          future.value match {
            case None    => future.onComplete(holder)(pekko.dispatch.ExecutionContexts.parasitic)
            case Some(v) =>
              // #20217 the future is already here, optimization: avoid scheduling it on the dispatcher and
              // run the logic directly on this thread
              holder.setElem(v)
              v match {
                // this optimization also requires us to stop the stage to fail fast if the decider says so:
                case Failure(ex) if holder.supervisionDirectiveFor(decider, ex) == Supervision.Stop => failStage(ex)
                case _                                                                              => pushNextIfPossible()
              }
          }

        } catch {
          // this logic must only be executed if f throws, not if the future is failed
          case NonFatal(ex) => if (decider(ex) == Supervision.Stop) failStage(ex)
        }

        pullIfNeeded()
      }

      override def onUpstreamFinish(): Unit = if (buffer.isEmpty) completeStage()

      @tailrec
      private def pushNextIfPossible(): Unit =
        if (buffer.isEmpty) pullIfNeeded()
        else if (buffer.peek().elem eq NotYetThere) pullIfNeeded() // ahead of line blocking to keep order
        else if (isAvailable(out)) {
          val holder = buffer.dequeue()
          holder.elem match {
            case Success(elem) =>
              if (elem != null) {
                push(out, elem)
                pullIfNeeded()
              } else {
                // elem is null
                pullIfNeeded()
                pushNextIfPossible()
              }

            case Failure(NonFatal(ex)) =>
              holder.supervisionDirectiveFor(decider, ex) match {
                // this could happen if we are looping in pushNextIfPossible and end up on a failed future before the
                // onComplete callback has run
                case Supervision.Stop => failStage(ex)
                case _                =>
                  // try next element
                  pushNextIfPossible()
              }
            case Failure(ex) =>
              // fatal exception in buffer, not sure that it can actually happen, but for good measure
              throw ex
          }
        }

      private def pullIfNeeded(): Unit = {
        if (isClosed(in) && buffer.isEmpty) completeStage()
        else if (buffer.used < parallelism && !hasBeenPulled(in)) tryPull(in)
        // else already pulled and waiting for next element
      }

      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class MapAsyncUnordered[In, Out](parallelism: Int, f: In => Future[Out])
    extends GraphStage[FlowShape[In, Out]] {
  require(parallelism >= 1, "parallelism must be at least 1")

  private val in = Inlet[In]("MapAsyncUnordered.in")
  private val out = Outlet[Out]("MapAsyncUnordered.out")

  override def initialAttributes = DefaultAttributes.mapAsyncUnordered and SourceLocation.forLambda(f)

  override val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      override def toString = s"MapAsyncUnordered.Logic(inFlight=$inFlight, buffer=$buffer)"

      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var inFlight = 0
      private var buffer: BufferImpl[Out] = _
      private val invokeFutureCB: Try[Out] => Unit = getAsyncCallback(futureCompleted).invoke

      private[this] def todo: Int = inFlight + buffer.used

      override def preStart(): Unit = buffer = BufferImpl(parallelism, inheritedAttributes)

      def futureCompleted(result: Try[Out]): Unit = {
        def isCompleted = isClosed(in) && todo == 0
        inFlight -= 1
        result match {
          case Success(elem) if elem != null =>
            if (isAvailable(out)) {
              if (!hasBeenPulled(in)) tryPull(in)
              push(out, elem)
              if (isCompleted) completeStage()
            } else buffer.enqueue(elem)
          case Success(_) =>
            if (isCompleted) completeStage()
            else if (!hasBeenPulled(in)) tryPull(in)
          case Failure(ex) =>
            if (decider(ex) == Supervision.Stop) failStage(ex)
            else if (isCompleted) completeStage()
            else if (!hasBeenPulled(in)) tryPull(in)
        }
      }

      override def onPush(): Unit = {
        try {
          val future = f(grab(in))
          inFlight += 1
          future.value match {
            case None    => future.onComplete(invokeFutureCB)(pekko.dispatch.ExecutionContexts.parasitic)
            case Some(v) => futureCompleted(v)
          }
        } catch {
          case NonFatal(ex) => if (decider(ex) == Supervision.Stop) failStage(ex)
        }
        if (todo < parallelism && !hasBeenPulled(in)) tryPull(in)
      }

      override def onUpstreamFinish(): Unit = {
        if (todo == 0) completeStage()
      }

      override def onPull(): Unit = {
        if (!buffer.isEmpty) push(out, buffer.dequeue())

        val leftTodo = todo
        if (isClosed(in) && leftTodo == 0) completeStage()
        else if (leftTodo < parallelism && !hasBeenPulled(in)) tryPull(in)
      }

      setHandlers(in, out, this)
    }
}

@InternalApi private[pekko] final case class Watch[T](targetRef: ActorRef) extends SimpleLinearGraphStage[T] {

  override def initialAttributes = DefaultAttributes.watch

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler with StageLogging {
      override protected def logSource: Class[_] = classOf[Watch[_]]

      override def preStart(): Unit = {
        val self = getStageActor {
          case (_, Terminated(`targetRef`)) =>
            failStage(new WatchedActorTerminatedException("Watch", targetRef))
          case (_, _) => // keep the compiler happy (stage actor receive is total)
        }
        self.watch(targetRef)
      }

      override def onPull(): Unit =
        pull(in)

      override def onPush(): Unit =
        push(out, grab(in))

      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class Log[T](name: String, extract: T => Any, logAdapter: Option[LoggingAdapter])
    extends SimpleLinearGraphStage[T] {

  override def toString = "Log"

  // TODO more optimisations can be done here - prepare logOnPush function etc
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler with InHandler {

      import Log._

      private var logLevels: LogLevels = _
      private var log: LoggingAdapter = _

      def decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      override def preStart(): Unit = {
        logLevels = inheritedAttributes.get[LogLevels](DefaultLogLevels)
        log = logAdapter match {
          case Some(l) => l
          case _       =>
            Logging(materializer.system, materializer)(fromMaterializer)
        }
      }

      override def onPush(): Unit = {
        try {
          val elem = grab(in)
          if (isEnabled(logLevels.onElement))
            log.log(logLevels.onElement, "[{}] Element: {}", name, extract(elem))

          push(out, elem)
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop => failStage(ex)
              case _                => pull(in)
            }
        }
      }

      override def onPull(): Unit = pull(in)

      override def onUpstreamFailure(cause: Throwable): Unit = {
        if (isEnabled(logLevels.onFailure))
          logLevels.onFailure match {
            case Logging.ErrorLevel => log.error(cause, "[{}] Upstream failed.", name)
            case level              =>
              log.log(
                level,
                "[{}] Upstream failed, cause: {}: {}",
                name,
                Logging.simpleName(cause.getClass),
                cause.getMessage)
          }

        super.onUpstreamFailure(cause)
      }

      override def onUpstreamFinish(): Unit = {
        if (isEnabled(logLevels.onFinish))
          log.log(logLevels.onFinish, "[{}] Upstream finished.", name)

        super.onUpstreamFinish()
      }

      override def onDownstreamFinish(cause: Throwable): Unit = {
        if (isEnabled(logLevels.onFinish))
          log.log(
            logLevels.onFinish,
            "[{}] Downstream finished, cause: {}: {}",
            name,
            Logging.simpleName(cause.getClass),
            cause.getMessage)

        super.onDownstreamFinish(cause: Throwable)
      }

      private def isEnabled(l: LogLevel): Boolean = l.asInt != OffInt

      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object Log {

  /**
   * Must be located here to be visible for implicit resolution, when [[Materializer]] is passed to [[Logging]]
   * More specific LogSource than `fromString`, which would add the ActorSystem name in addition to the supervision to the log source.
   */
  final val fromMaterializer = new LogSource[Materializer] {

    // do not expose private context classes (of OneBoundedInterpreter)
    override def getClazz(t: Materializer): Class[_] = classOf[Materializer]

    override def genString(t: Materializer): String = {
      try s"$DefaultLoggerName(${t.supervisor.path})"
      catch {
        case _: Exception => LogSource.fromString.genString(DefaultLoggerName)
      }
    }

  }

  private final val DefaultLoggerName = "org.apache.pekko.stream.Log"
  private final val OffInt = LogLevels.Off.asInt
  private final val DefaultLogLevels =
    LogLevels(onElement = Logging.DebugLevel, onFinish = Logging.DebugLevel, onFailure = Logging.ErrorLevel)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class LogWithMarker[T](
    name: String,
    marker: T => LogMarker,
    extract: T => Any,
    logAdapter: Option[MarkerLoggingAdapter])
    extends SimpleLinearGraphStage[T] {

  override def toString = "LogWithMarker"

  // TODO more optimisations can be done here - prepare logOnPush function etc
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler with InHandler {

      import LogWithMarker._

      private var logLevels: LogLevels = _
      private var log: MarkerLoggingAdapter = _

      def decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      override def preStart(): Unit = {
        logLevels = inheritedAttributes.get[LogLevels](DefaultLogLevels)
        log = logAdapter match {
          case Some(l) => l
          case _       =>
            Logging.withMarker(materializer.system, materializer)(fromMaterializer)
        }
      }

      override def onPush(): Unit = {
        try {
          val elem = grab(in)
          if (isEnabled(logLevels.onElement))
            log.log(marker(elem), logLevels.onElement, log.format("[{}] Element: {}", name, extract(elem)))

          push(out, elem)
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop => failStage(ex)
              case _                => pull(in)
            }
        }
      }

      override def onPull(): Unit = pull(in)

      override def onUpstreamFailure(cause: Throwable): Unit = {
        if (isEnabled(logLevels.onFailure))
          logLevels.onFailure match {
            case Logging.ErrorLevel => log.error(cause, "[{}] Upstream failed.", name)
            case level              =>
              log.log(
                level,
                "[{}] Upstream failed, cause: {}: {}",
                name,
                Logging.simpleName(cause.getClass),
                cause.getMessage)
          }

        super.onUpstreamFailure(cause)
      }

      override def onUpstreamFinish(): Unit = {
        if (isEnabled(logLevels.onFinish))
          log.log(logLevels.onFinish, "[{}] Upstream finished.", name)

        super.onUpstreamFinish()
      }

      override def onDownstreamFinish(cause: Throwable): Unit = {
        if (isEnabled(logLevels.onFinish))
          log.log(
            logLevels.onFinish,
            "[{}] Downstream finished, cause: {}: {}",
            name,
            Logging.simpleName(cause.getClass),
            cause.getMessage)

        super.onDownstreamFinish(cause: Throwable)
      }

      private def isEnabled(l: LogLevel): Boolean = l.asInt != OffInt

      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object LogWithMarker {

  /**
   * Must be located here to be visible for implicit resolution, when [[Materializer]] is passed to [[Logging]]
   * More specific LogSource than `fromString`, which would add the ActorSystem name in addition to the supervision to the log source.
   */
  final val fromMaterializer = new LogSource[Materializer] {

    // do not expose private context classes (of OneBoundedInterpreter)
    override def getClazz(t: Materializer): Class[_] = classOf[Materializer]

    override def genString(t: Materializer): String = {
      try s"$DefaultLoggerName(${t.supervisor.path})"
      catch {
        case _: Exception => LogSource.fromString.genString(DefaultLoggerName)
      }
    }

  }

  private final val DefaultLoggerName = "org.apache.pekko.stream.LogWithMarker"
  private final val OffInt = LogLevels.Off.asInt
  private final val DefaultLogLevels =
    LogLevels(onElement = Logging.DebugLevel, onFinish = Logging.DebugLevel, onFailure = Logging.ErrorLevel)
}

@InternalApi private[pekko] object GroupedWeightedWithin {
  val groupedWeightedWithinTimer = "GroupedWeightedWithinTimer"
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class GroupedWeightedWithin[T](
    val maxWeight: Long,
    val maxNumber: Int,
    val costFn: T => Long,
    val interval: FiniteDuration)
    extends GraphStage[FlowShape[T, immutable.Seq[T]]] {
  require(maxWeight > 0, "maxWeight must be greater than 0")
  require(maxNumber > 0, "maxNumber must be greater than 0")
  require(interval > Duration.Zero)

  val in = Inlet[T]("in")
  val out = Outlet[immutable.Seq[T]]("out")

  override def initialAttributes = DefaultAttributes.groupedWeightedWithin and SourceLocation.forLambda(costFn)

  val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with InHandler with OutHandler {

      private var builder: VectorBuilder[T] = new VectorBuilder
      private var pending: T = null.asInstanceOf[T]
      private var pendingWeight: Long = 0L
      // True if:
      // - buf is nonEmpty
      //       AND
      // - (timer fired
      //        OR
      //    totalWeight >= maxWeight
      //        OR
      //    pending != null
      //        OR
      //    upstream completed)
      private var pushEagerly = false
      private var groupEmitted = true
      private var finished = false
      private var totalWeight = 0L
      private var totalNumber = 0
      private var hasElements = false
      private val contextPropagation = ContextPropagation()

      override def preStart() = {
        scheduleWithFixedDelay(GroupedWeightedWithin.groupedWeightedWithinTimer, interval, interval)
        pull(in)
      }

      private def nextElement(elem: T): Unit = {
        groupEmitted = false
        val cost = costFn(elem)
        if (cost < 0L)
          failStage(new IllegalArgumentException(s"Negative weight [$cost] for element [$elem] is not allowed"))
        else {
          hasElements = true
          // if there is place (both weight and number) for `elem` in the current group
          if (totalWeight + cost <= maxWeight && totalNumber + 1 <= maxNumber) {
            builder += elem
            totalWeight += cost
            totalNumber += 1;

            // if potentially there is a place (both weight and number) for one more element in the current group
            if (totalWeight < maxWeight && totalNumber < maxNumber) pull(in)
            else {
              if (!isAvailable(out)) {
                // we should emit group when downstream becomes available
                pushEagerly = true
                // we want to pull anyway, since we allow for zero weight elements
                // but since `emitGroup()` will pull internally (by calling `startNewGroup()`)
                // we also have to pull if downstream hasn't yet requested an element.
                pull(in)
              } else {
                scheduleWithFixedDelay(GroupedWeightedWithin.groupedWeightedWithinTimer, interval, interval)
                emitGroup()
              }
            }
          } else {
            // if there is a single heavy element that weighs more than the limit
            if (totalWeight == 0L && totalNumber == 0) {
              builder += elem
              totalWeight += cost
              totalNumber += 1;
              pushEagerly = true
            } else {
              pending = elem
              pendingWeight = cost
            }
            scheduleWithFixedDelay(GroupedWeightedWithin.groupedWeightedWithinTimer, interval, interval)
            tryCloseGroup()
          }
        }
      }

      private def tryCloseGroup(): Unit = {
        if (isAvailable(out)) emitGroup()
        else if (pending != null || finished) pushEagerly = true
      }

      private def emitGroup(): Unit = {
        groupEmitted = true
        contextPropagation.resumeContext()
        push(out, builder.result())
        if (finished) {
          builder = null // free for GC
          if (pending != null) emit(out, Vector(pending), () => completeStage())
          else completeStage()
        } else {
          builder.clear()
          startNewGroup()
        }
      }

      private def startNewGroup(): Unit = {
        if (pending != null) {
          totalWeight = pendingWeight
          totalNumber = 1
          pendingWeight = 0L
          builder += pending
          pending = null.asInstanceOf[T]
          groupEmitted = false
        } else {
          totalWeight = 0L
          totalNumber = 0
          hasElements = false
        }
        pushEagerly = false
        if (isAvailable(in)) nextElement(grab(in))
        else if (!hasBeenPulled(in)) pull(in)
      }

      override def onPush(): Unit = {
        contextPropagation.suspendContext()
        if (pending == null) nextElement(grab(in)) // otherwise keep the element for next round
      }

      override def onPull(): Unit = if (pushEagerly) emitGroup()

      override def onUpstreamFinish(): Unit = {
        finished = true
        if (groupEmitted) {
          builder = null // free for GC
          completeStage()
        } else tryCloseGroup()
      }

      override protected def onTimer(timerKey: Any) = if (hasElements) {
        if (isAvailable(out)) emitGroup()
        else pushEagerly = true
      }

      setHandlers(in, out, this)
    }
}

/**
 * INTERNAL API
 */
@InternalApi object Delay {
  private val TimerName = "DelayedTimer"
  private val DelayPrecisionMS = 10
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class Delay[T](
    delayStrategySupplier: () => DelayStrategy[T],
    overflowStrategy: DelayOverflowStrategy)
    extends SimpleLinearGraphStage[T] {

  override def initialAttributes: Attributes = DefaultAttributes.delay

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with InHandler with OutHandler {
      import Delay._

      private[this] val size = inheritedAttributes.mandatoryAttribute[InputBuffer].max

      private[this] val delayStrategy = delayStrategySupplier()

      // buffer has pairs of timestamp of expected push and element
      private[this] val buffer = BufferImpl[(Long, T)](size, inheritedAttributes)

      private[this] val onPushWhenBufferFull: () => Unit = overflowStrategy match {
        case EmitEarly =>
          () => {
            if (isAvailable(out)) {
              if (isTimerActive(TimerName)) {
                cancelTimer(TimerName)
              }

              push(out, buffer.dequeue()._2)
              grabAndPull()
              completeIfReady()
            } else {
              throw new IllegalStateException(
                "Was configured to emitEarly and got element when out is not ready and buffer is full, should not be possible.")
            }
          }
        case _: DropHead =>
          () => {
            buffer.dropHead()
            grabAndPull()
          }
        case _: DropTail =>
          () => {
            buffer.dropTail()
            grabAndPull()
          }
        case _: DropNew =>
          () => {
            grab(in)
            if (shouldPull) pull(in)
          }
        case _: DropBuffer =>
          () => {
            buffer.clear()
            grabAndPull()
          }
        case _: Fail =>
          () => {
            failStage(new BufferOverflowException(s"Buffer overflow for delay operator (max capacity was: $size)!"))
          }
        case _: Backpressure =>
          () => {
            throw new IllegalStateException("Delay buffer must never overflow in Backpressure mode")
          }
      }

      def onPush(): Unit = {
        if (buffer.isFull)
          onPushWhenBufferFull()
        else {
          grabAndPull()
          if (!isTimerActive(TimerName)) {
            val waitTime = nextElementWaitTime()
            if (waitTime <= DelayPrecisionMS && isAvailable(out)) {
              push(out, buffer.dequeue()._2)
              completeIfReady()
            } else
              scheduleOnce(TimerName, waitTime.millis)
          }
        }
      }

      private def shouldPull: Boolean =
        buffer.used < size || !overflowStrategy.isBackpressure ||
        // we can only emit early if output is ready
        (overflowStrategy == EmitEarly && isAvailable(out))

      private def grabAndPull(): Unit = {
        val element = grab(in)
        buffer.enqueue((System.nanoTime() + delayStrategy.nextDelay(element).toNanos, element))
        if (shouldPull) pull(in)
      }

      override def onUpstreamFinish(): Unit =
        completeIfReady()

      def onPull(): Unit = {
        if (!isTimerActive(TimerName) && !buffer.isEmpty) {
          val waitTime = nextElementWaitTime()
          if (waitTime <= DelayPrecisionMS)
            push(out, buffer.dequeue()._2)
          else
            scheduleOnce(TimerName, waitTime.millis)
        }

        if (!isClosed(in) && !hasBeenPulled(in) && shouldPull)
          pull(in)

        completeIfReady()
      }

      setHandler(in, this)
      setHandler(out, this)

      def completeIfReady(): Unit = if (isClosed(in) && buffer.isEmpty) completeStage()

      private def nextElementWaitTime(): Long = {
        NANOSECONDS.toMillis(buffer.peek()._1 - System.nanoTime())
      }

      final override protected def onTimer(key: Any): Unit = {
        if (isAvailable(out))
          push(out, buffer.dequeue()._2)

        completeIfReady()
      }
    }

  override def toString = "Delay"
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object TakeWithin {
  val takeWithinTimer = "TakeWithinTimer"
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class TakeWithin[T](val timeout: FiniteDuration) extends SimpleLinearGraphStage[T] {

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with InHandler with OutHandler {
      def onPush(): Unit = push(out, grab(in))

      def onPull(): Unit = pull(in)

      setHandlers(in, out, this)

      final override protected def onTimer(key: Any): Unit = completeStage()

      override def preStart(): Unit = scheduleOnce(TakeWithin.takeWithinTimer, timeout)
    }

  override def toString = "TakeWithin"
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class DropWithin[T](val timeout: FiniteDuration) extends SimpleLinearGraphStage[T] {
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {

      private val startNanoTime = System.nanoTime()
      private val timeoutInNano = timeout.toNanos

      def onPush(): Unit = {
        if (System.nanoTime() - startNanoTime <= timeoutInNano) {
          pull(in)
        } else {
          push(out, grab(in))
          // change the in handler to avoid System.nanoTime call after timeout
          setHandler(in,
            new InHandler {
              def onPush() = push(out, grab(in))
            })
        }
      }

      def onPull(): Unit = pull(in)

      setHandlers(in, out, this)

    }

  override def toString = "DropWithin"
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class Reduce[T](val f: (T, T) => T) extends SimpleLinearGraphStage[T] {
  override def initialAttributes: Attributes = DefaultAttributes.reduce and SourceLocation.forLambda(f)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler { self =>
      override def toString = s"Reduce.Logic(aggregator=$aggregator)"

      private var aggregator: T = _
      private val empty: T = aggregator

      private def decider =
        inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      def setInitialInHandler(): Unit = {
        // Initial input handler
        setHandler(in,
          new InHandler {
            override def onPush(): Unit = {
              aggregator = grab(in)
              pull(in)
              setHandler(in, self)
            }

            override def onUpstreamFinish(): Unit =
              failStage(new NoSuchElementException("reduce over empty stream"))
          })
      }

      @nowarn // compiler complaining about aggregator = _: T
      override def onPush(): Unit = {
        val elem = grab(in)
        try {
          aggregator = f(aggregator, elem)
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop    => failStage(ex)
              case Supervision.Restart =>
                aggregator = empty
                setInitialInHandler()
              case _ => ()

            }
        } finally {
          if (!isClosed(in)) pull(in)
        }
      }

      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit = {
        push(out, aggregator)
        completeStage()
      }

      setInitialInHandler()
      setHandler(out, self)
    }

  override def toString = "Reduce"
}

/**
 * INTERNAL API
 */
@InternalApi private[stream] object RecoverWith

@InternalApi private[pekko] final class RecoverWith[T, M](
    val maximumRetries: Int,
    val pf: PartialFunction[Throwable, Graph[SourceShape[T], M]])
    extends SimpleLinearGraphStage[T] {

  override def initialAttributes: Attributes = DefaultAttributes.recoverWith

  override def createLogic(attr: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      var attempt = 0

      override def onPush(): Unit = push(out, grab(in))
      override def onUpstreamFailure(ex: Throwable): Unit = onFailure(ex)

      override def onPull(): Unit = pull(in)

      @nowarn("msg=Any")
      def onFailure(ex: Throwable): Unit = {
        import Collect.NotApplied
        if (maximumRetries < 0 || attempt < maximumRetries) {
          pf.applyOrElse(ex, NotApplied) match {
            case _: NotApplied.type                                                                               => failStage(ex)
            case source: Graph[SourceShape[T] @unchecked, M @unchecked] if TraversalBuilder.isEmptySource(source) =>
              completeStage()
            case source: Graph[SourceShape[T] @unchecked, M @unchecked] =>
              TraversalBuilder.getValuePresentedSource(source) match {
                case OptionVal.Some(graph) => graph match {
                    case singleSource: SingleSource[T @unchecked] => emit(out, singleSource.elem, () => completeStage())
                    case failed: FailedSource[T @unchecked]       => failStage(failed.failure)
                    case futureSource: FutureSource[T @unchecked] => futureSource.future.value match {
                        case Some(Success(elem)) => emit(out, elem, () => completeStage())
                        case Some(Failure(ex))   => failStage(ex)
                        case None                =>
                          switchTo(source)
                          attempt += 1
                      }
                    case iterableSource: IterableSource[T @unchecked] =>
                      emitMultiple(out, iterableSource.elements, () => completeStage())
                    case javaStreamSource: JavaStreamSource[T @unchecked, _] =>
                      emitMultiple(out, javaStreamSource.open().spliterator(), () => completeStage())
                    case _ =>
                      switchTo(source)
                      attempt += 1
                  }
                case _ =>
                  switchTo(source)
                  attempt += 1
              }
            case _ => throw new IllegalStateException() // won't happen, compiler exhaustiveness check pleaser
          }
        } else
          failStage(ex)
      }

      def switchTo(source: Graph[SourceShape[T], M]): Unit = {
        val sinkIn = new SubSinkInlet[T]("RecoverWithSink") with InHandler with OutHandler { self =>
          override def onPush(): Unit = push(out, self.grab())
          override def onUpstreamFinish(): Unit = completeStage()
          override def onUpstreamFailure(ex: Throwable): Unit = onFailure(ex)

          override def onPull(): Unit = self.pull()
          override def onDownstreamFinish(cause: Throwable): Unit = self.cancel(cause)

          setHandler(self)
        }

        Source.fromGraph(source).runWith(sinkIn.sink)(interpreter.subFusingMaterializer)
        setHandler(out, sinkIn)
        if (isAvailable(out)) sinkIn.pull()
      }

      setHandlers(in, out, this)
    }

  override def toString: String = "RecoverWith"
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class StatefulMap[S, In, Out](create: () => S, f: (S, In) => (S, Out),
    onComplete: S => Option[Out])
    extends GraphStage[FlowShape[In, Out]] {
  require(create != null, "create function should not be null")
  require(f != null, "f function should not be null")
  require(onComplete != null, "onComplete function should not be null")

  private val in = Inlet[In]("StatefulMap.in")
  private val out = Outlet[Out]("StatefulMap.out")
  override val shape: FlowShape[In, Out] = FlowShape(in, out)

  override protected def initialAttributes: Attributes = Attributes(SourceLocation.forLambda(f))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      lazy val decider: Decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var state: S = _
      private var needInvokeOnCompleteCallback: Boolean = false

      override def preStart(): Unit = {
        state = create()
        needInvokeOnCompleteCallback = true
      }

      override def onPush(): Unit =
        try {
          val elem = grab(in)
          val (newState, newElem) = f(state, elem)
          state = newState
          push(out, newElem)
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop    => closeStateAndFail(ex)
              case Supervision.Resume  => pull(in)
              case Supervision.Restart => resetStateAndPull()
            }
        }

      override def onUpstreamFinish(): Unit = closeStateAndComplete()

      override def onUpstreamFailure(ex: Throwable): Unit = closeStateAndFail(ex)

      override def onDownstreamFinish(cause: Throwable): Unit = {
        needInvokeOnCompleteCallback = false
        onComplete(state)
        super.onDownstreamFinish(cause)
      }

      private def resetStateAndPull(): Unit = {
        needInvokeOnCompleteCallback = false
        onComplete(state)
        state = create()
        needInvokeOnCompleteCallback = true;
        pull(in)
      }

      private def closeStateAndComplete(): Unit = {
        needInvokeOnCompleteCallback = false
        onComplete(state) match {
          case Some(elem) => emit(out, elem, () => completeStage())
          case None       => completeStage()
        }
      }

      private def closeStateAndFail(ex: Throwable): Unit = {
        needInvokeOnCompleteCallback = false
        onComplete(state) match {
          case Some(elem) => emit(out, elem, () => failStage(ex))
          case None       => failStage(ex)
        }
      }

      override def onPull(): Unit = pull(in)

      override def postStop(): Unit = {
        if (needInvokeOnCompleteCallback) {
          onComplete(state)
        }
      }

      setHandlers(in, out, this)
    }

  override def toString = "StatefulMap"
}

/**
 * INTERNAL API
 */
@InternalApi
@ccompatUsedUntil213
private[pekko] final class StatefulMapConcat[In, Out](val factory: StatefulMapConcatAccumulatorFactory[In, Out])
    extends GraphStage[FlowShape[In, Out]] {
  val in = Inlet[In]("StatefulMapConcat.in")
  val out = Outlet[Out]("StatefulMapConcat.out")
  override val shape = FlowShape(in, out)

  def this(f: () => In => IterableOnce[Out]) = this {
    import StatefulMapConcatAccumulatorFactory._
    f match {
      case factory: StatefulMapConcatAccumulatorFactory[In, Out] @unchecked => factory
      case _                                                                => f.asFactory
    }
  }

  override def initialAttributes: Attributes = DefaultAttributes.statefulMapConcat and SourceLocation.forLambda(factory)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
    private var currentIterator: Iterator[Out] = _
    private var accumulator: StatefulMapConcatAccumulator[In, Out] = factory.accumulator()
    private val contextPropagation = ContextPropagation()

    private def hasNext = if (currentIterator ne null) currentIterator.hasNext else false

    setHandlers(in, out, this)

    def pushPull(shouldResumeContext: Boolean): Unit =
      if (hasNext) {
        if (shouldResumeContext) contextPropagation.resumeContext()
        push(out, currentIterator.next())
        if (hasNext) {
          // suspend context for the next element
          contextPropagation.suspendContext()
        } else if (isClosed(in)) emitMaybeLastElementsAndComplete()
      } else if (!isClosed(in))
        pull(in)
      else emitMaybeLastElementsAndComplete()

    private def emitMaybeLastElementsAndComplete(): Unit = {
      val maybeElements = accumulator.onComplete()
      val maybeElementsItr = maybeElements.iterator
      if (maybeElementsItr.hasNext) {
        emitMultiple(out, maybeElementsItr, () => completeStage())
      } else {
        completeStage()
      }
    }

    override def onPush(): Unit =
      try {
        currentIterator = accumulator(grab(in)).iterator
        pushPull(shouldResumeContext = false)
      } catch handleException

    override def onUpstreamFinish(): Unit = if (!hasNext) emitMaybeLastElementsAndComplete()

    override def onPull(): Unit =
      try pushPull(shouldResumeContext = true)
      catch handleException

    private def handleException: Catcher[Unit] = {
      case NonFatal(ex) =>
        decider(ex) match {
          case Supervision.Stop   => failStage(ex)
          case Supervision.Resume =>
            if (isClosed(in)) emitMaybeLastElementsAndComplete()
            else if (!hasBeenPulled(in)) pull(in)
          case Supervision.Restart =>
            if (isClosed(in)) emitMaybeLastElementsAndComplete()
            else {
              restartState()
              if (!hasBeenPulled(in)) pull(in)
            }
        }
    }

    private def restartState(): Unit = {
      accumulator = factory.accumulator()
      currentIterator = null
    }
  }

  override def toString = "StatefulMapConcat"

}
