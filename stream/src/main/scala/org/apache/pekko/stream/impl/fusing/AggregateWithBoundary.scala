/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl.fusing

import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko

import pekko.annotation.InternalApi
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.Supervision
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler, TimerGraphStageLogic }

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final case class AggregateWithBoundary[In, Agg, Out](
    allocate: () => Agg,
    aggregate: (Agg, In) => (Agg, Boolean),
    harvest: Agg => Out,
    emitOnTimer: Option[(Agg => Boolean, FiniteDuration)])
    extends GraphStage[FlowShape[In, Out]] {

  emitOnTimer.foreach {
    case (_, interval) => require(interval.gteq(1.milli), s"timer(${interval.toCoarsest}) must not be smaller than 1ms")
  }

  val in: Inlet[In] = Inlet[In](s"${this.getClass.getName}.in")
  val out: Outlet[Out] = Outlet[Out](s"${this.getClass.getName}.out")
  override val shape: FlowShape[In, Out] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with InHandler with OutHandler {
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var aggregated: Agg = null.asInstanceOf[Agg]

      override def preStart(): Unit = {
        emitOnTimer.foreach {
          case (_, interval) => scheduleWithFixedDelay(s"${this.getClass.getSimpleName}Timer", interval, interval)
        }
      }

      override protected def onTimer(timerKey: Any): Unit = {
        emitOnTimer.foreach {
          case (isReadyOnTimer, _) =>
            if (aggregated != null) {
              val maybeReadyToEmit =
                try isReadyOnTimer(aggregated)
                catch {
                  case NonFatal(ex) =>
                    decider(ex) match {
                      case Supervision.Stop =>
                        failStage(ex)
                      case Supervision.Resume =>
                        aggregated = null.asInstanceOf[Agg]
                      case Supervision.Restart =>
                        restartState()
                    }
                    false
                }
              if (maybeReadyToEmit) harvestAndEmit(pullOnRecover = false)
            }
        }
      }

      // at onPush, isAvailable(in)=true hasBeenPulled(in)=false, isAvailable(out) could be true or false due to timer triggered emit
      override def onPush(): Unit = {
        val inElem = grab(in)
        if (aggregated == null)
          try aggregated = allocate()
          catch {
            case NonFatal(ex) =>
              decider(ex) match {
                case Supervision.Stop =>
                  failStage(ex)
                case Supervision.Resume =>
                  pullIfPossible()
                case Supervision.Restart =>
                  restartState()
                  pullIfPossible()
              }
              return
          }

        val shouldEmit =
          try {
            val (updated, result) = aggregate(aggregated, inElem)
            aggregated = updated
            result
          } catch {
            case NonFatal(ex) =>
              decider(ex) match {
                case Supervision.Stop =>
                  failStage(ex)
                case Supervision.Resume =>
                  restartState()
                  pullIfPossible()
                case Supervision.Restart =>
                  restartState()
                  pullIfPossible()
              }
              return
          }

        if (shouldEmit) harvestAndEmit(pullOnRecover = true)
        else {
          // the decision to pull entirely depend on isAvailable(out)=true
          // if isAvailable(out)=false, this means timer has caused emit, cannot pull or it could emit indefinitely bypassing back pressure
          pullIfPossible()
        }
      }

      override def onUpstreamFinish(): Unit = {
        // Note that emit is asynchronous, it will keep the stage alive until downstream actually take the element
        if (aggregated != null)
          try {
            emit(out, harvest(aggregated))
            aggregated = null.asInstanceOf[Agg]
            completeStage()
          } catch {
            case NonFatal(ex) =>
              decider(ex) match {
                case Supervision.Stop =>
                  failStage(ex)
                case Supervision.Resume =>
                  aggregated = null.asInstanceOf[Agg]
                  completeStage()
                case Supervision.Restart =>
                  restartState()
                  completeStage()
              }
          }
        else completeStage()
      }

      // at onPull, isAvailable(out) is always true indicating downstream is waiting
      // isAvailable(in) and hasBeenPulled(in) can be (true, false) (false, true) or (false, false)
      override def onPull(): Unit = if (!hasBeenPulled(in)) pull(in)

      setHandlers(in, out, this)

      private def restartState(): Unit =
        aggregated = null.asInstanceOf[Agg]

      private def pullIfPossible(): Unit =
        if (isAvailable(out) && !isClosed(in) && !hasBeenPulled(in)) pull(in)

      private def harvestAndEmit(pullOnRecover: Boolean): Unit =
        try {
          emit(out, harvest(aggregated))
          aggregated = null.asInstanceOf[Agg]
        } catch {
          case NonFatal(ex) =>
            decider(ex) match {
              case Supervision.Stop =>
                failStage(ex)
              case Supervision.Resume =>
                aggregated = null.asInstanceOf[Agg]
                if (pullOnRecover) pullIfPossible()
              case Supervision.Restart =>
                restartState()
                if (pullOnRecover) pullIfPossible()
            }
        }

    }

}
