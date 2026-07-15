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

package org.apache.pekko.stream.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.annotation.unchecked.uncheckedVariance
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._
import scala.reflect.ClassTag

import org.apache.pekko

import org.jspecify.annotations.Nullable

import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.ApiMayChange
import pekko.event.{ LogMarker, LoggingAdapter, MarkerLoggingAdapter }
import pekko.japi.Pair
import pekko.japi.function
import pekko.stream._
import pekko.util.ConstantFun

object SourceWithContext {

  /**
   * Creates a SourceWithContext from a regular flow that operates on `Pair<data, context>` elements.
   */
  def fromPairs[Out, CtxOut, Mat](under: Source[Pair[Out, CtxOut], Mat]): SourceWithContext[Out, CtxOut, Mat] = {
    new SourceWithContext(scaladsl.SourceWithContext.fromTuples(under.asScala.map(_.toScala)))
  }

  /**
   * Creates a SourceWithContext from an existing base SourceWithContext outputting an optional element
   * and applying an additional viaFlow only if the element in the stream is defined.
   *
   * '''Emits when''' the provided viaFlow runs with defined elements
   *
   * '''Backpressures when''' the viaFlow runs for the defined elements and downstream backpressures
   *
   * '''Completes when''' upstream completes
   *
   * '''Cancels when''' downstream cancels
   *
   * @param source The base source that outputs an optional element
   * @param viaFlow The flow that gets used if the optional element in is defined. This flow only works
   *                on the data portion of flow and ignores the context so this flow *must* not re-order,
   *                drop or emit multiple elements for one incoming element
   * @param combine How to combine the materialized values of source and viaFlow
   * @return a SourceWithContext with the viaFlow applied onto defined elements of the flow. The output value
   *         is contained within an Optional which indicates whether the original source's element had viaFlow
   *         applied.
   * @since 1.1.0
   */
  @ApiMayChange
  def unsafeOptionalDataVia[SOut, FOut, Ctx, SMat, FMat, Mat](source: SourceWithContext[Optional[SOut], Ctx, SMat],
      viaFlow: Flow[SOut, FOut, FMat],
      combine: function.Function2[SMat, FMat, Mat]
  ): SourceWithContext[Optional[FOut], Ctx, Mat] =
    scaladsl.SourceWithContext.unsafeOptionalDataVia(source.map(_.toScala).asScala, viaFlow.asScala)(
      combinerToScala(combine)).map(
      _.toJava).asJava

}

/**
 * A source that provides operations which automatically propagate the context of an element.
 * Only a subset of common operations from [[Source]] is supported. As an escape hatch you can
 * use [[SourceWithContext#via]] to manually provide the context propagation for otherwise unsupported
 * operations.
 *
 * Can be created by calling [[Source.asSourceWithContext]]
 */
final class SourceWithContext[+Out, +Ctx, +Mat](delegate: scaladsl.SourceWithContext[Out, Ctx, Mat])
    extends GraphDelegate(delegate) {

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
   * @see [[pekko.stream.javadsl.Flow.via]]
   */
  def via[Out2, Ctx2, Mat2](
      viaFlow: Graph[FlowShape[Pair[Out @uncheckedVariance, Ctx @uncheckedVariance], Pair[Out2, Ctx2]], Mat2])
      : SourceWithContext[Out2, Ctx2, Mat] =
    viaScala(_.via(pekko.stream.scaladsl.Flow[(Out, Ctx)].map { case (o, c) => Pair(o, c) }.via(viaFlow).map(
      _.toScala)))

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
  @ApiMayChange def unsafeDataVia[Out2, Mat2](
      viaFlow: Graph[FlowShape[Out @uncheckedVariance, Out2], Mat2]): SourceWithContext[Out2, Ctx, Mat] =
    viaScala(_.unsafeDataVia(viaFlow))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.withAttributes]].
   *
   * @see [[pekko.stream.javadsl.Source.withAttributes]]
   */
  override def withAttributes(attr: Attributes): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.withAttributes(attr))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.mapError]].
   *
   * @see [[pekko.stream.javadsl.Source.mapError]]
   */
  def mapError(pf: PartialFunction[Throwable, Throwable]): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.mapError(pf))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.mapMaterializedValue]].
   *
   * @see [[pekko.stream.javadsl.Flow.mapMaterializedValue]]
   */
  def mapMaterializedValue[Mat2](f: function.Function[Mat, Mat2]): SourceWithContext[Out, Ctx, Mat2] =
    viaScala(_.mapMaterializedValue(f.apply _))

  /**
   * Stops automatic context propagation from here and converts this to a regular
   * stream of a pair of (data, context).
   */
  def asSource(): Source[Pair[Out @uncheckedVariance, Ctx @uncheckedVariance], Mat @uncheckedVariance] =
    delegate.asSource.map { case (o, c) => Pair(o, c) }.asJava

  // remaining operations in alphabetic order

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.collect]].
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see [[pekko.stream.javadsl.Source.collect]]
   */
  def collect[Out2](pf: PartialFunction[Out, Out2]): SourceWithContext[Out2, Ctx, Mat] =
    viaScala(_.collect(pf))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.collectFirst]].
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see [[pekko.stream.javadsl.Source.collectFirst]]
   * @since 2.0.0
   */
  def collectFirst[Out2](pf: PartialFunction[Out, Out2]): SourceWithContext[Out2, Ctx, Mat] =
    viaScala(_.collectFirst(pf))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.collectWhile]].
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see [[pekko.stream.javadsl.Source.collectWhile]]
   * @since 2.0.0
   */
  def collectWhile[Out2](pf: PartialFunction[Out, Out2]): SourceWithContext[Out2, Ctx, Mat] =
    viaScala(_.collectWhile(pf))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.collectType]].
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see [[pekko.stream.javadsl.Source.collectType]]
   * @since 2.0.0
   */
  def collectType[Out2](clazz: Class[Out2]): SourceWithContext[Out2, Ctx, Mat] =
    viaScala(_.collectType[Out2](ClassTag[Out2](clazz)))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.filter]].
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see [[pekko.stream.javadsl.Source.filter]]
   */
  def filter(p: function.Predicate[Out]): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.filter(p.test))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.filterNot]].
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see [[pekko.stream.javadsl.Source.filterNot]]
   */
  def filterNot(p: function.Predicate[Out]): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.filterNot(p.test))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.dropRepeated]].
   *
   * @see [[pekko.stream.javadsl.Source.dropRepeated]]
   * @since 2.0.0
   */
  def dropRepeated(): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.dropRepeated())

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.dropRepeated]].
   *
   * @see [[pekko.stream.javadsl.Source.dropRepeated]]
   * @since 2.0.0
   */
  def dropRepeated(p: function.Function2[Out, Out, Boolean]): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.dropRepeated((left, right) => p.apply(left, right)))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.dropWhile]].
   *
   * @see [[pekko.stream.javadsl.Source.dropWhile]]
   * @since 2.0.0
   */
  def dropWhile(p: function.Predicate[Out]): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.dropWhile(p.test))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.drop]].
   *
   * @see [[pekko.stream.javadsl.Source.drop]]
   * @since 2.0.0
   */
  def drop(n: Long): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.drop(n))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.dropWithin]].
   *
   * @see [[pekko.stream.javadsl.Source.dropWithin]]
   * @since 2.0.0
   */
  def dropWithin(duration: java.time.Duration): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.dropWithin(duration.toScala))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.grouped]].
   *
   * Each output group will be associated with a `Seq` of corresponding context elements.
   *
   * @see [[pekko.stream.javadsl.Source.grouped]]
   */
  def grouped(
      n: Int): SourceWithContext[java.util.List[Out @uncheckedVariance], java.util.List[Ctx @uncheckedVariance], Mat] =
    viaScala(_.grouped(n).map(_.asJava).mapContext(_.asJava))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.map]].
   *
   * @see [[pekko.stream.javadsl.Source.map]]
   */
  def map[Out2](f: function.Function[Out, Out2]): SourceWithContext[Out2, Ctx, Mat] =
    viaScala(_.map(f.apply))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.mapOption]].
   *
   * Note, that the context of elements that are filtered out is skipped as well.
   *
   * @see [[pekko.stream.javadsl.Source.mapOption]]
   * @since 2.0.0
   */
  def mapOption[Out2](f: function.Function[Out, Optional[Out2]]): SourceWithContext[Out2, Ctx, Mat] =
    viaScala(_.mapOption(out => f.apply(out).toScala))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.mapAsync]].
   *
   * @see [[pekko.stream.javadsl.Source.mapAsync]]
   */
  def mapAsync[Out2](
      parallelism: Int,
      f: function.Function[Out, CompletionStage[Out2]]): SourceWithContext[Out2, Ctx, Mat] =
    viaScala(_.mapAsync[Out2](parallelism)(o => f.apply(o).asScala))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.mapAsyncPartitioned]].
   *
   * @since 1.1.0
   * @see [[pekko.stream.javadsl.Source.mapAsyncPartitioned]]
   */
  def mapAsyncPartitioned[Out2, P](
      parallelism: Int,
      partitioner: function.Function[Out, P],
      f: function.Function2[Out, P, CompletionStage[Out2]]): SourceWithContext[Out2, Ctx, Mat] = {
    viaScala(_.mapAsyncPartitioned(parallelism)(partitioner(_))(f(_, _).asScala))
  }

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.mapAsyncPartitionedUnordered]].
   *
   * @since 1.1.0
   * @see [[pekko.stream.javadsl.Source.mapAsyncPartitionedUnordered]]
   */
  def mapAsyncPartitionedUnordered[Out2, P](
      parallelism: Int,
      partitioner: function.Function[Out, P],
      f: function.Function2[Out, P, CompletionStage[Out2]]): SourceWithContext[Out2, Ctx, Mat] =
    viaScala(_.mapAsyncPartitionedUnordered(parallelism)(partitioner(_))(f(_, _).asScala))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.mapConcat]].
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
   * @see [[pekko.stream.javadsl.Source.mapConcat]]
   */
  def mapConcat[Out2](f: function.Function[Out, ? <: java.lang.Iterable[Out2]]): SourceWithContext[Out2, Ctx, Mat] =
    viaScala(_.mapConcat(elem => f.apply(elem).asScala))

  /**
   * Apply the given function to each context element (leaving the data elements unchanged).
   */
  def mapContext[Ctx2](extractContext: function.Function[Ctx, Ctx2]): SourceWithContext[Out, Ctx2, Mat] =
    viaScala(_.mapContext(extractContext.apply))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.limit]].
   *
   * @see [[pekko.stream.javadsl.Source.limit]]
   * @since 2.0.0
   */
  def limit(max: Int): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.limit(max))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.limitWeighted]].
   *
   * @see [[pekko.stream.javadsl.Source.limitWeighted]]
   * @since 2.0.0
   */
  def limitWeighted(max: Long, costFn: function.Function[Out, java.lang.Long]): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.limitWeighted(max)(costFn.apply))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.sliding]].
   *
   * Each output group will be associated with a `Seq` of corresponding context elements.
   *
   * @see [[pekko.stream.javadsl.Source.sliding]]
   */
  def sliding(n: Int, step: Int = 1)
      : SourceWithContext[java.util.List[Out @uncheckedVariance], java.util.List[Ctx @uncheckedVariance], Mat] =
    viaScala(_.sliding(n, step).map(_.asJava).mapContext(_.asJava))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.log]].
   *
   * @see [[pekko.stream.javadsl.Source.log]]
   */
  def log(name: String, extract: function.Function[Out, Any], @Nullable log: LoggingAdapter)
      : SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.log(name, e => extract.apply(e))(log))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Flow.log]].
   *
   * @see [[pekko.stream.javadsl.Flow.log]]
   */
  def log(name: String, extract: function.Function[Out, Any]): SourceWithContext[Out, Ctx, Mat] =
    this.log(name, extract, null)

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Flow.log]].
   *
   * @see [[pekko.stream.javadsl.Flow.log]]
   */
  def log(name: String, @Nullable log: LoggingAdapter): SourceWithContext[Out, Ctx, Mat] =
    this.log(name, ConstantFun.javaIdentityFunction[Out], log)

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Flow.log]].
   *
   * @see [[pekko.stream.javadsl.Flow.log]]
   */
  def log(name: String): SourceWithContext[Out, Ctx, Mat] =
    this.log(name, ConstantFun.javaIdentityFunction[Out], null)

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.logWithMarker]].
   *
   * @see [[pekko.stream.javadsl.Source.logWithMarker]]
   */
  def logWithMarker(
      name: String,
      marker: function.Function2[Out, Ctx, LogMarker],
      extract: function.Function[Out, Any],
      @Nullable log: MarkerLoggingAdapter): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.logWithMarker(name, (e, c) => marker.apply(e, c), e => extract.apply(e))(log))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Flow.logWithMarker]].,
   *
   * @see [[pekko.stream.javadsl.Flow.logWithMarker]]
   */
  def logWithMarker(
      name: String,
      marker: function.Function2[Out, Ctx, LogMarker],
      extract: function.Function[Out, Any]): SourceWithContext[Out, Ctx, Mat] =
    this.logWithMarker(name, marker, extract, null)

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Flow.logWithMarker]].
   *
   * @see [[pekko.stream.javadsl.Flow.logWithMarker]]
   */
  def logWithMarker(
      name: String,
      marker: function.Function2[Out, Ctx, LogMarker],
      @Nullable log: MarkerLoggingAdapter): SourceWithContext[Out, Ctx, Mat] =
    this.logWithMarker(name, marker, ConstantFun.javaIdentityFunction[Out], log)

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Flow.logWithMarker]].
   *
   * @see [[pekko.stream.javadsl.Flow.logWithMarker]]
   */
  def logWithMarker(name: String, marker: function.Function2[Out, Ctx, LogMarker]): SourceWithContext[Out, Ctx, Mat] =
    this.logWithMarker(name, marker, ConstantFun.javaIdentityFunction[Out], null)

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.takeWhile]].
   *
   * @see [[pekko.stream.javadsl.Source.takeWhile]]
   * @since 2.0.0
   */
  def takeWhile(p: function.Predicate[Out], inclusive: Boolean): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.takeWhile(p.test, inclusive))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.takeWhile]].
   *
   * @see [[pekko.stream.javadsl.Source.takeWhile]]
   * @since 2.0.0
   */
  def takeWhile(p: function.Predicate[Out]): SourceWithContext[Out, Ctx, Mat] =
    takeWhile(p, inclusive = false)

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.takeUntil]].
   *
   * @see [[pekko.stream.javadsl.Source.takeUntil]]
   * @since 2.0.0
   */
  def takeUntil(p: function.Predicate[Out]): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.takeUntil(p.test))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.take]].
   *
   * @see [[pekko.stream.javadsl.Source.take]]
   * @since 2.0.0
   */
  def take(n: Long): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.take(n))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.takeWithin]].
   *
   * @see [[pekko.stream.javadsl.Source.takeWithin]]
   * @since 2.0.0
   */
  def takeWithin(duration: java.time.Duration): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.takeWithin(duration.toScala))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.throttle]].
   *
   * @see [[pekko.stream.javadsl.Source.throttle]]
   */
  def throttle(elements: Int, per: java.time.Duration): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.throttle(elements, per.toScala))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.throttle]].
   *
   * @see [[pekko.stream.javadsl.Source.throttle]]
   */
  def throttle(
      elements: Int,
      per: java.time.Duration,
      maximumBurst: Int,
      mode: ThrottleMode): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.throttle(elements, per.toScala, maximumBurst, mode))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.throttle]].
   *
   * @see [[pekko.stream.javadsl.Source.throttle]]
   */
  def throttle(
      cost: Int,
      per: java.time.Duration,
      costCalculation: function.Function[Out, Integer]): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.throttle(cost, per.toScala, costCalculation.apply))

  /**
   * Context-preserving variant of [[pekko.stream.javadsl.Source.throttle]].
   *
   * @see [[pekko.stream.javadsl.Source.throttle]]
   */
  def throttle(
      cost: Int,
      per: java.time.Duration,
      maximumBurst: Int,
      costCalculation: function.Function[Out, Integer],
      mode: ThrottleMode): SourceWithContext[Out, Ctx, Mat] =
    viaScala(_.throttle(cost, per.toScala, maximumBurst, costCalculation.apply, mode))

  /**
   * Connect this [[pekko.stream.javadsl.SourceWithContext]] to a [[pekko.stream.javadsl.Sink]],
   * concatenating the processing steps of both.
   */
  def to[Mat2](
      sink: Graph[SinkShape[Pair[Out @uncheckedVariance, Ctx @uncheckedVariance]], Mat2]): javadsl.RunnableGraph[Mat] =
    RunnableGraph.fromGraph(asScala.asSource.map { case (o, e) => Pair(o, e) }.to(sink))

  /**
   * Connect this [[pekko.stream.javadsl.SourceWithContext]] to a [[pekko.stream.javadsl.Sink]],
   * concatenating the processing steps of both.
   */
  def toMat[Mat2, Mat3](
      sink: Graph[SinkShape[Pair[Out @uncheckedVariance, Ctx @uncheckedVariance]], Mat2],
      combine: function.Function2[Mat, Mat2, Mat3]): javadsl.RunnableGraph[Mat3] =
    RunnableGraph.fromGraph(asScala.asSource.map { case (o, e) => Pair(o, e) }.toMat(sink)(combinerToScala(combine)))

  /**
   * Connect this [[pekko.stream.javadsl.SourceWithContext]] to a [[pekko.stream.javadsl.Sink]] and run it.
   * The returned value is the materialized value of the `Sink`.
   */
  def runWith[M](
      sink: Graph[SinkShape[Pair[Out @uncheckedVariance, Ctx @uncheckedVariance]], M],
      systemProvider: ClassicActorSystemProvider): M =
    toMat(sink, Keep.right[Mat, M]).run(systemProvider.classicSystem)

  /**
   * Connect this [[pekko.stream.javadsl.SourceWithContext]] to a [[pekko.stream.javadsl.Sink]] and run it.
   * The returned value is the materialized value of the `Sink`.
   *
   * Prefer the method taking an ActorSystem unless you have special requirements.
   */
  def runWith[M](
      sink: Graph[SinkShape[Pair[Out @uncheckedVariance, Ctx @uncheckedVariance]], M],
      materializer: Materializer): M =
    toMat(sink, Keep.right[Mat, M]).run(materializer)

  def asScala: scaladsl.SourceWithContext[Out, Ctx, Mat] = delegate

  private[this] def viaScala[Out2, Ctx2, Mat2](
      f: scaladsl.SourceWithContext[Out, Ctx, Mat] => scaladsl.SourceWithContext[Out2, Ctx2, Mat2])
      : SourceWithContext[Out2, Ctx2, Mat2] =
    new SourceWithContext(f(delegate))
}
