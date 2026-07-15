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
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

import org.apache.pekko

import pekko.NotUsed
import pekko.annotation.ApiMayChange
import pekko.event.{ LogMarker, LoggingAdapter, MarkerLoggingAdapter }
import pekko.stream._
import pekko.stream.impl.Throttle
import pekko.util.ConstantFun

/**
 * Shared stream operations for [[FlowWithContext]] and [[SourceWithContext]] that automatically propagate a context
 * element with each data element.
 */
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
   * @see `FlowOps.via`
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
   * Data variant of `FlowOps.alsoTo`
   *
   * @see `FlowOps.alsoTo`
   * @since 1.1.0
   */
  def alsoTo(that: Graph[SinkShape[Out], ?]): Repr[Out, Ctx]

  /**
   * Data variant of `FlowOps.alsoTo` with configurable cancellation propagation.
   *
   * @see `FlowOps.alsoTo`
   * @since 2.0.0
   */
  def alsoTo(that: Graph[SinkShape[Out], ?], propagateCancellation: Boolean): Repr[Out, Ctx]

  /**
   * Context variant of `FlowOps.alsoTo`
   *
   * @see `FlowOps.alsoTo`
   * @since 1.1.0
   */
  def alsoToContext(that: Graph[SinkShape[Ctx], ?]): Repr[Out, Ctx]

  /**
   * Data variant of `FlowOps.wireTap`
   *
   * @see `FlowOps.wireTap`
   * @since 1.1.0
   */
  def wireTap(that: Graph[SinkShape[Out], ?]): Repr[Out, Ctx]

  /**
   * Context variant of `FlowOps.wireTap`
   *
   * @see `FlowOps.wireTap`
   * @since 1.1.0
   */
  def wireTapContext(that: Graph[SinkShape[Ctx], ?]): Repr[Out, Ctx]

  /**
   * Context-preserving variant of `FlowOps.map`.
   *
   * @see `FlowOps.map`
   */
  def map[Out2](f: Out => Out2): Repr[Out2, Ctx] =
    via(flow.map { case (e, ctx) => (f(e), ctx) })

  /**
   * Context-preserving variant of `FlowOps.mapOption`.
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see `FlowOps.mapOption`
   * @since 2.0.0
   */
  def mapOption[Out2](f: Out => Option[Out2]): Repr[Out2, Ctx] =
    via(flow.mapOption { case (e, ctx) => f(e).map(_ -> ctx) })

  /**
   * Context-preserving variant of `FlowOps.mapError`.
   *
   * @see `FlowOps.mapError`
   */
  def mapError(pf: PartialFunction[Throwable, Throwable]): Repr[Out, Ctx] =
    via(flow.mapError(pf))

  /**
   * Context-preserving variant of `FlowOps.mapAsync`.
   *
   * @see `FlowOps.mapAsync`
   */
  def mapAsync[Out2](parallelism: Int)(f: Out => Future[Out2]): Repr[Out2, Ctx] =
    via(flow.mapAsync(parallelism) {
      case (e, ctx) => f(e).map(o => (o, ctx))(ExecutionContext.parasitic)
    })

  /**
   * Context-preserving variant of `FlowOps.mapAsyncPartitioned`.
   *
   * @since 1.1.0
   * @see `FlowOps.mapAsyncPartitioned`
   */
  def mapAsyncPartitioned[Out2, P](parallelism: Int)(
      partitioner: Out => P)(
      f: (Out, P) => Future[Out2]): Repr[Out2, Ctx] = {
    via(flow[Out, Ctx].mapAsyncPartitioned(parallelism)(pair => partitioner(pair._1)) {
      (pair, partition) =>
        f(pair._1, partition).map((_, pair._2))(ExecutionContext.parasitic)
    })
  }

  /**
   * Context-preserving variant of `FlowOps.mapAsyncPartitionedUnordered`.
   *
   * @since 1.1.0
   * @see `FlowOps.mapAsyncPartitionedUnordered`
   */
  def mapAsyncPartitionedUnordered[Out2, P](parallelism: Int)(
      partitioner: Out => P)(
      f: (Out, P) => Future[Out2]): Repr[Out2, Ctx] = {
    via(flow[Out, Ctx].mapAsyncPartitionedUnordered(parallelism)(pair => partitioner(pair._1)) {
      (pair, partition) =>
        f(pair._1, partition).map((_, pair._2))(ExecutionContext.parasitic)
    })
  }

  /**
   * Context-preserving variant of `FlowOps.collect`.
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see `FlowOps.collect`
   */
  def collect[Out2](f: PartialFunction[Out, Out2]): Repr[Out2, Ctx] =
    via(flow.collect {
      case (e, ctx) if f.isDefinedAt(e) => (f(e), ctx)
    })

  /**
   * Context-preserving variant of `FlowOps.filter`.
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see `FlowOps.filter`
   */
  def filter(pred: Out => Boolean): Repr[Out, Ctx] =
    collect { case e if pred(e) => e }

  /**
   * Context-preserving variant of `FlowOps.filterNot`.
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see `FlowOps.filterNot`
   */
  def filterNot(pred: Out => Boolean): Repr[Out, Ctx] =
    collect { case e if !pred(e) => e }

  /**
   * Alias for [[filter]], added to enable filtering in for comprehensions.
   *
   * @see `FlowOps.withFilter`
   * @since 2.0.0
   */
  @ApiMayChange
  def withFilter(pred: Out => Boolean): Repr[Out, Ctx] =
    filter(pred)

  /**
   * Context-preserving variant of `FlowOps.dropRepeated`.
   *
   * @see `FlowOps.dropRepeated`
   * @since 2.0.0
   */
  def dropRepeated(): Repr[Out, Ctx] =
    dropRepeated(ConstantFun.scalaAnyTwoEquals)

  /**
   * Context-preserving variant of `FlowOps.dropRepeated`.
   *
   * @see `FlowOps.dropRepeated`
   * @since 2.0.0
   */
  def dropRepeated(pred: (Out, Out) => Boolean): Repr[Out, Ctx] =
    via(flow.dropRepeated((left, right) => pred(left._1, right._1)))

  /**
   * Context-preserving variant of `FlowOps.takeWhile`.
   *
   * @see `FlowOps.takeWhile`
   * @since 2.0.0
   */
  def takeWhile(pred: Out => Boolean): Repr[Out, Ctx] =
    takeWhile(pred, inclusive = false)

  /**
   * Context-preserving variant of `FlowOps.takeUntil`.
   *
   * @see `FlowOps.takeUntil`
   * @since 2.0.0
   */
  def takeUntil(pred: Out => Boolean): Repr[Out, Ctx] =
    takeWhile(!pred(_), inclusive = true)

  /**
   * Context-preserving variant of `FlowOps.takeWhile`.
   *
   * @see `FlowOps.takeWhile`
   * @since 2.0.0
   */
  def takeWhile(pred: Out => Boolean, inclusive: Boolean): Repr[Out, Ctx] =
    via(flow.takeWhile({ case (e, _) => pred(e) }, inclusive))

  /**
   * Context-preserving variant of `FlowOps.dropWhile`.
   *
   * @see `FlowOps.dropWhile`
   * @since 2.0.0
   */
  def dropWhile(pred: Out => Boolean): Repr[Out, Ctx] =
    via(flow.dropWhile { case (e, _) => pred(e) })

  /**
   * Context-preserving variant of `FlowOps.collectFirst`.
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see `FlowOps.collectFirst`
   * @since 2.0.0
   */
  def collectFirst[Out2](f: PartialFunction[Out, Out2]): Repr[Out2, Ctx] =
    via(flow.collectFirst {
      case (e, ctx) if f.isDefinedAt(e) => (f(e), ctx)
    })

  /**
   * Context-preserving variant of `FlowOps.collectWhile`.
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see `FlowOps.collectWhile`
   * @since 2.0.0
   */
  def collectWhile[Out2](f: PartialFunction[Out, Out2]): Repr[Out2, Ctx] =
    via(flow.collectWhile {
      case (e, ctx) if f.isDefinedAt(e) => (f(e), ctx)
    })

  /**
   * Context-preserving variant of `FlowOps.collectType`.
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see `FlowOps.collectType`
   * @since 2.0.0
   */
  def collectType[Out2](implicit tag: ClassTag[Out2]): Repr[Out2, Ctx] =
    collect { case tag(e) => e }

  /**
   * Context-preserving variant of `FlowOps.drop`.
   *
   * @see `FlowOps.drop`
   * @since 2.0.0
   */
  def drop(n: Long): Repr[Out, Ctx] =
    via(flow.drop(n))

  /**
   * Context-preserving variant of `FlowOps.dropWithin`.
   *
   * @see `FlowOps.dropWithin`
   * @since 2.0.0
   */
  def dropWithin(d: FiniteDuration): Repr[Out, Ctx] =
    via(flow.dropWithin(d))

  /**
   * Context-preserving variant of `FlowOps.take`.
   *
   * @see `FlowOps.take`
   * @since 2.0.0
   */
  def take(n: Long): Repr[Out, Ctx] =
    via(flow.take(n))

  /**
   * Context-preserving variant of `FlowOps.takeWithin`.
   *
   * @see `FlowOps.takeWithin`
   * @since 2.0.0
   */
  def takeWithin(d: FiniteDuration): Repr[Out, Ctx] =
    via(flow.takeWithin(d))

  /**
   * Context-preserving variant of `FlowOps.limit`.
   *
   * @see `FlowOps.limit`
   * @since 2.0.0
   */
  def limit(max: Long): Repr[Out, Ctx] =
    limitWeighted(max)(_ => 1)

  /**
   * Context-preserving variant of `FlowOps.limitWeighted`.
   *
   * @see `FlowOps.limitWeighted`
   * @since 2.0.0
   */
  def limitWeighted(max: Long)(costFn: Out => Long): Repr[Out, Ctx] =
    via(flow.limitWeighted(max) { case (e, _) => costFn(e) })

  /**
   * Context-preserving variant of `FlowOps.grouped`.
   *
   * Each output group will be associated with a `Seq` of corresponding context elements.
   *
   * @see `FlowOps.grouped`
   */
  def grouped(n: Int): Repr[immutable.Seq[Out], immutable.Seq[Ctx]] =
    via(flow.grouped(n).map { elsWithContext =>
      val (els, ctxs) = elsWithContext.unzip
      (els, ctxs)
    })

  /**
   * Context-preserving variant of `FlowOps.sliding`.
   *
   * Each output group will be associated with a `Seq` of corresponding context elements.
   *
   * @see `FlowOps.sliding`
   */
  def sliding(n: Int, step: Int = 1): Repr[immutable.Seq[Out], immutable.Seq[Ctx]] =
    via(flow.sliding(n, step).map { elsWithContext =>
      val (els, ctxs) = elsWithContext.unzip
      (els, ctxs)
    })

  /**
   * Context-preserving variant of `FlowOps.mapConcat`.
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
   * @see `FlowOps.mapConcat`
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
   * Context-preserving variant of `FlowOps.log`.
   *
   * @see `FlowOps.log`
   */
  def log(name: String, extract: Out => Any = ConstantFun.scalaIdentityFunction)(
      implicit log: LoggingAdapter = null): Repr[Out, Ctx] = {
    val extractWithContext: ((Out, Ctx)) => Any = { case (e, _) => extract(e) }
    via(flow.log(name, extractWithContext)(log))
  }

  /**
   * Context-preserving variant of `FlowOps.logWithMarker`.
   *
   * @see `FlowOps.logWithMarker`
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
   * Context-preserving variant of `FlowOps.throttle`.
   *
   * @see `FlowOps.throttle`
   */
  def throttle(elements: Int, per: FiniteDuration): Repr[Out, Ctx] =
    throttle(elements, per, Throttle.AutomaticMaximumBurst, ConstantFun.oneInt, ThrottleMode.Shaping)

  /**
   * Context-preserving variant of `FlowOps.throttle`.
   *
   * @see `FlowOps.throttle`
   */
  def throttle(elements: Int, per: FiniteDuration, maximumBurst: Int, mode: ThrottleMode): Repr[Out, Ctx] =
    throttle(elements, per, maximumBurst, ConstantFun.oneInt, mode)

  /**
   * Context-preserving variant of `FlowOps.throttle`.
   *
   * @see `FlowOps.throttle`
   */
  def throttle(cost: Int, per: FiniteDuration, costCalculation: (Out) => Int): Repr[Out, Ctx] =
    throttle(cost, per, Throttle.AutomaticMaximumBurst, costCalculation, ThrottleMode.Shaping)

  /**
   * Context-preserving variant of `FlowOps.throttle`.
   *
   * @see `FlowOps.throttle`
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
