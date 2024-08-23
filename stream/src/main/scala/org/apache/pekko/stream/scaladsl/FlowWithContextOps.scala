/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.ApiMayChange
import pekko.dispatch.ExecutionContexts
import pekko.event.{ LogMarker, LoggingAdapter, MarkerLoggingAdapter }
import pekko.stream._
import pekko.stream.impl.Throttle
import pekko.util.ConstantFun
import pekko.util.ccompat._

/**
 * Shared stream operations for [[FlowWithContext]] and [[SourceWithContext]] that automatically propagate a context
 * element with each data element.
 */
@ccompatUsedUntil213
trait FlowWithContextOps[+Out, +Ctx, +Mat] {
  type ReprMat[+O, +C, +M] <: FlowWithContextOps[O, C, M] {
    type ReprMat[+OO, +CC, +MatMat] = FlowWithContextOps.this.ReprMat[OO, CC, MatMat]
  }
  type Repr[+O, +C] = ReprMat[O, C, Mat @uncheckedVariance]

  /**
   * Transform this flow by the regular flow. The given flow must support manual context propagation by
   * taking and producing tuples of (data, context).
   *
   *  It is up to the implementer to ensure the inner flow does not exhibit any behavior that is not expected
   *  by the downstream elements, such as reordering. For more background on these requirements
   *  see https://pekko.apache.org/docs/pekko/current/stream/stream-context.html.
   *
   * This can be used as an escape hatch for operations that are not (yet) provided with automatic
   * context propagation here.
   *
   * @see [[pekko.stream.scaladsl.FlowOps.via]]
   */
  def via[Out2, Ctx2, Mat2](flow: Graph[FlowShape[(Out, Ctx), (Out2, Ctx2)], Mat2]): Repr[Out2, Ctx2]

  /**
   * Transform this flow by the regular flow. The given flow works on the data portion of the stream and
   * ignores the context.
   *
   * The given flow *must* not re-order, drop or emit multiple elements for one incoming
   * element, the sequence of incoming contexts is re-combined with the outgoing
   * elements of the stream. If a flow not fulfilling this requirement is used the stream
   * will not fail but continue running in a corrupt state and re-combine incorrect pairs
   * of elements and contexts or deadlock.
   *
   * For more background on these requirements
   *  see https://pekko.apache.org/docs/pekko/current/stream/stream-context.html.
   */
  @ApiMayChange def unsafeDataVia[Out2, Mat2](viaFlow: Graph[FlowShape[Out, Out2], Mat2]): Repr[Out2, Ctx]

  /**
   * Transform this flow by the regular flow. The given flow must support manual context propagation by
   * taking and producing tuples of (data, context).
   *
   *  It is up to the implementer to ensure the inner flow does not exhibit any behavior that is not expected
   *  by the downstream elements, such as reordering. For more background on these requirements
   *  see https://pekko.apache.org/docs/pekko/current/stream/stream-context.html.
   *
   * This can be used as an escape hatch for operations that are not (yet) provided with automatic
   * context propagation here.
   *
   * The `combine` function is used to compose the materialized values of this flow and that
   * flow into the materialized value of the resulting Flow.
   *
   * @see [[pekko.stream.scaladsl.FlowOpsMat.viaMat]]
   */
  def viaMat[Out2, Ctx2, Mat2, Mat3](flow: Graph[FlowShape[(Out, Ctx), (Out2, Ctx2)], Mat2])(
      combine: (Mat, Mat2) => Mat3): ReprMat[Out2, Ctx2, Mat3]

  /**
   * Data variant of [[pekko.stream.scaladsl.FlowOps.alsoTo]]
   *
   * @see [[pekko.stream.scaladsl.FlowOps.alsoTo]]
   * @since 1.1.0
   */
  def alsoTo(that: Graph[SinkShape[Out], _]): Repr[Out, Ctx]

  /**
   * Context variant of [[pekko.stream.scaladsl.FlowOps.alsoTo]]
   *
   * @see [[pekko.stream.scaladsl.FlowOps.alsoTo]]
   * @since 1.1.0
   */
  def alsoToContext(that: Graph[SinkShape[Ctx], _]): Repr[Out, Ctx]

  /**
   * Data variant of [[pekko.stream.scaladsl.FlowOps.wireTap]]
   *
   * @see [[pekko.stream.scaladsl.FlowOps.wireTap]]
   * @since 1.1.0
   */
  def wireTap(that: Graph[SinkShape[Out], _]): Repr[Out, Ctx]

  /**
   * Context variant of [[pekko.stream.scaladsl.FlowOps.wireTap]]
   *
   * @see [[pekko.stream.scaladsl.FlowOps.wireTap]]
   * @since 1.1.0
   */
  def wireTapContext(that: Graph[SinkShape[Ctx], _]): Repr[Out, Ctx]

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.map]].
   *
   * @see [[pekko.stream.scaladsl.FlowOps.map]]
   */
  def map[Out2](f: Out => Out2): Repr[Out2, Ctx] =
    via(flow.map { case (e, ctx) => (f(e), ctx) })

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.mapError]].
   *
   * @see [[pekko.stream.scaladsl.FlowOps.mapError]]
   */
  def mapError(pf: PartialFunction[Throwable, Throwable]): Repr[Out, Ctx] =
    via(flow.mapError(pf))

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.mapAsync]].
   *
   * @see [[pekko.stream.scaladsl.FlowOps.mapAsync]]
   */
  def mapAsync[Out2](parallelism: Int)(f: Out => Future[Out2]): Repr[Out2, Ctx] =
    via(flow.mapAsync(parallelism) {
      case (e, ctx) => f(e).map(o => (o, ctx))(ExecutionContexts.parasitic)
    })

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.mapAsyncPartitioned]].
   *
   * @since 1.1.0
   * @see [[pekko.stream.scaladsl.FlowOps.mapAsyncPartitioned]]
   */
  def mapAsyncPartitioned[Out2, P](parallelism: Int)(
      partitioner: Out => P)(
      f: (Out, P) => Future[Out2]): Repr[Out2, Ctx] = {
    via(flow[Out, Ctx].mapAsyncPartitioned(parallelism)(pair => partitioner(pair._1)) {
      (pair, partition) =>
        f(pair._1, partition).map((_, pair._2))(ExecutionContexts.parasitic)
    })
  }

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.mapAsyncPartitionedUnordered]].
   *
   * @since 1.1.0
   * @see [[pekko.stream.scaladsl.FlowOps.mapAsyncPartitionedUnordered]]
   */
  def mapAsyncPartitionedUnordered[Out2, P](parallelism: Int)(
      partitioner: Out => P)(
      f: (Out, P) => Future[Out2]): Repr[Out2, Ctx] = {
    via(flow[Out, Ctx].mapAsyncPartitionedUnordered(parallelism)(pair => partitioner(pair._1)) {
      (pair, partition) =>
        f(pair._1, partition).map((_, pair._2))(ExecutionContexts.parasitic)
    })
  }

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.collect]].
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see [[pekko.stream.scaladsl.FlowOps.collect]]
   */
  def collect[Out2](f: PartialFunction[Out, Out2]): Repr[Out2, Ctx] =
    via(flow.collect {
      case (e, ctx) if f.isDefinedAt(e) => (f(e), ctx)
    })

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.filter]].
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see [[pekko.stream.scaladsl.FlowOps.filter]]
   */
  def filter(pred: Out => Boolean): Repr[Out, Ctx] =
    collect { case e if pred(e) => e }

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.filterNot]].
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see [[pekko.stream.scaladsl.FlowOps.filterNot]]
   */
  def filterNot(pred: Out => Boolean): Repr[Out, Ctx] =
    collect { case e if !pred(e) => e }

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.grouped]].
   *
   * Each output group will be associated with a `Seq` of corresponding context elements.
   *
   * @see [[pekko.stream.scaladsl.FlowOps.grouped]]
   */
  def grouped(n: Int): Repr[immutable.Seq[Out], immutable.Seq[Ctx]] =
    via(flow.grouped(n).map { elsWithContext =>
      val (els, ctxs) = elsWithContext.unzip
      (els, ctxs)
    })

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.sliding]].
   *
   * Each output group will be associated with a `Seq` of corresponding context elements.
   *
   * @see [[pekko.stream.scaladsl.FlowOps.sliding]]
   */
  def sliding(n: Int, step: Int = 1): Repr[immutable.Seq[Out], immutable.Seq[Ctx]] =
    via(flow.sliding(n, step).map { elsWithContext =>
      val (els, ctxs) = elsWithContext.unzip
      (els, ctxs)
    })

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.mapConcat]].
   *
   * The context of the input element will be associated with each of the output elements calculated from
   * this input element.
   *
   * Example:
   *
   * ```
   * def dup(element: String) = Seq(element, element)
   *
   * Input:
   *
   * ("a", 1)
   * ("b", 2)
   *
   * inputElements.mapConcat(dup)
   *
   * Output:
   *
   * ("a", 1)
   * ("a", 1)
   * ("b", 2)
   * ("b", 2)
   * ```
   *
   * @see [[pekko.stream.scaladsl.FlowOps.mapConcat]]
   */
  def mapConcat[Out2](f: Out => IterableOnce[Out2]): Repr[Out2, Ctx] =
    via(flow.mapConcat {
      case (e, ctx) => f(e).iterator.map(_ -> ctx)
    })

  /**
   * Apply the given function to each context element (leaving the data elements unchanged).
   */
  def mapContext[Ctx2](f: Ctx => Ctx2): Repr[Out, Ctx2] =
    via(flow.map { case (e, ctx) => (e, f(ctx)) })

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.log]].
   *
   * @see [[pekko.stream.scaladsl.FlowOps.log]]
   */
  def log(name: String, extract: Out => Any = ConstantFun.scalaIdentityFunction)(
      implicit log: LoggingAdapter = null): Repr[Out, Ctx] = {
    val extractWithContext: ((Out, Ctx)) => Any = { case (e, _) => extract(e) }
    via(flow.log(name, extractWithContext)(log))
  }

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.logWithMarker]].
   *
   * @see [[pekko.stream.scaladsl.FlowOps.logWithMarker]]
   */
  def logWithMarker(
      name: String,
      marker: (Out, Ctx) => LogMarker,
      extract: Out => Any = ConstantFun.scalaIdentityFunction)(
      implicit log: MarkerLoggingAdapter = null): Repr[Out, Ctx] = {
    val extractWithContext: ((Out, Ctx)) => Any = { case (e, _) => extract(e) }
    via(flow.logWithMarker(name, marker.tupled, extractWithContext)(log))
  }

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.throttle]].
   *
   * @see [[pekko.stream.scaladsl.FlowOps.throttle]]
   */
  def throttle(elements: Int, per: FiniteDuration): Repr[Out, Ctx] =
    throttle(elements, per, Throttle.AutomaticMaximumBurst, ConstantFun.oneInt, ThrottleMode.Shaping)

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.throttle]].
   *
   * @see [[pekko.stream.scaladsl.FlowOps.throttle]]
   */
  def throttle(elements: Int, per: FiniteDuration, maximumBurst: Int, mode: ThrottleMode): Repr[Out, Ctx] =
    throttle(elements, per, maximumBurst, ConstantFun.oneInt, mode)

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.throttle]].
   *
   * @see [[pekko.stream.scaladsl.FlowOps.throttle]]
   */
  def throttle(cost: Int, per: FiniteDuration, costCalculation: (Out) => Int): Repr[Out, Ctx] =
    throttle(cost, per, Throttle.AutomaticMaximumBurst, costCalculation, ThrottleMode.Shaping)

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.FlowOps.throttle]].
   *
   * @see [[pekko.stream.scaladsl.FlowOps.throttle]]
   */
  def throttle(
      cost: Int,
      per: FiniteDuration,
      maximumBurst: Int,
      costCalculation: (Out) => Int,
      mode: ThrottleMode): Repr[Out, Ctx] =
    via(flow.throttle(cost, per, maximumBurst, a => costCalculation(a._1), mode))

  private[pekko] def flow[T, C]: Flow[(T, C), (T, C), NotUsed] = Flow[(T, C)]
}
