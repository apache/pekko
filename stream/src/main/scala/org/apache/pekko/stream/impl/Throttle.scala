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

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.ThrottleMode.Enforcing
import pekko.stream._
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.stage._
import pekko.util.NanoTimeTokenBucket

import scala.concurrent.duration.{ FiniteDuration, _ }

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object Throttle {
  final val AutomaticMaximumBurst = -1
  private case object TimerKey
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class Throttle[T](
    val cost: Int,
    val per: FiniteDuration,
    val maximumBurst: Int,
    val costCalculation: (T) => Int,
    val mode: ThrottleMode)
    extends SimpleLinearGraphStage[T] {
  require(cost > 0, "cost must be > 0")
  require(per.toNanos > 0, "per time must be > 0")
  require(per.toNanos >= cost, "Rates larger than 1 unit / nanosecond are not supported")

  // There is some loss of precision here because of rounding, but this only happens if nanosBetweenTokens is very
  // small which is usually at rates where that precision is highly unlikely anyway as the overhead of this stage
  // is likely higher than the required accuracy interval.
  private val nanosBetweenTokens = per.toNanos / cost
  // 100 ms is a realistic minimum between tokens, otherwise the maximumBurst is adjusted
  // to be able to support higher rates
  val effectiveMaximumBurst: Long =
    if (maximumBurst == Throttle.AutomaticMaximumBurst) math.max(1, (100 * 1000 * 1000) / nanosBetweenTokens)
    else maximumBurst
  require(!(mode == ThrottleMode.Enforcing && effectiveMaximumBurst < 0), "maximumBurst must be > 0 in Enforcing mode")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with InHandler with OutHandler {
      private val tokenBucket = new NanoTimeTokenBucket(effectiveMaximumBurst, nanosBetweenTokens)
      private var currentElement: T = _

      override def preStart(): Unit = tokenBucket.init()

      override def onUpstreamFinish(): Unit =
        if (!(isAvailable(out) && isTimerActive(Throttle.TimerKey))) {
          completeStage()
        }

      override def onPush(): Unit = {
        val elem = grab(in)
        val cost = costCalculation(elem)
        val delayNanos = tokenBucket.offer(cost)

        if (delayNanos == 0L) push(out, elem)
        else {
          if (mode eq Enforcing) failStage(new RateExceededException("Maximum throttle throughput exceeded."))
          else {
            currentElement = elem
            scheduleOnce(Throttle.TimerKey, delayNanos.nanos)
          }
        }
      }

      override def onPull(): Unit = pull(in)

      override protected def onTimer(key: Any): Unit = {
        push(out, currentElement)
        currentElement = null.asInstanceOf[T]
        if (isClosed(in)) completeStage()
      }

      setHandlers(in, out, this)
    }

  override def toString = "Throttle"
}
