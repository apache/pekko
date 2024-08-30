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

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.NotUsed
import pekko.japi.Pair
import pekko.stream._

object FlowWithContext {

  /**
   * Creates an "empty" FlowWithContext that passes elements through with their context unchanged.
   */
  def apply[In, Ctx]: FlowWithContext[In, Ctx, In, Ctx, pekko.NotUsed] = {
    val under = Flow[(In, Ctx)]
    new FlowWithContext[In, Ctx, In, Ctx, pekko.NotUsed](under)
  }

  /**
   * Creates a FlowWithContext from a regular flow that operates on a tuple of `(data, context)` elements.
   */
  def fromTuples[In, CtxIn, Out, CtxOut, Mat](
      flow: Flow[(In, CtxIn), (Out, CtxOut), Mat]): FlowWithContext[In, CtxIn, Out, CtxOut, Mat] =
    new FlowWithContext(flow)

  /**
   * Creates a FlowWithContext from an existing base FlowWithContext outputting an optional element
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
   * @param flow The base flow that outputs an optional element
   * @param viaFlow The flow that gets used if the optional element in is defined. This flow only works
   *                on the data portion of flow and ignores the context so this flow *must* not re-order,
   *                drop or emit multiple elements for one incoming element
   * @param combine How to combine the materialized values of flow and viaFlow
   * @return a FlowWithContext with the viaFlow applied onto defined elements of the flow. The output value
   *         is contained within an Option which indicates whether the original flow's element had viaFlow
   *         applied.
   * @since 1.1.0
   */
  @ApiMayChange
  def unsafeOptionalDataVia[FIn, FOut, FViaOut, Ctx, FMat, FViaMat, Mat](
      flow: FlowWithContext[FIn, Ctx, Option[FOut], Ctx, FMat],
      viaFlow: Flow[FOut, FViaOut, FViaMat])(
      combine: (FMat, FViaMat) => Mat
  ): FlowWithContext[FIn, Ctx, Option[FViaOut], Ctx, Mat] =
    FlowWithContext.fromTuples(Flow.fromGraph(GraphDSL.createGraph(flow, viaFlow)(combine) {
      implicit b => (f, viaF) =>
        import GraphDSL.Implicits._
        val broadcast = b.add(Broadcast[(Option[FOut], Ctx)](2))
        val merge = b.add(Merge[(Option[FViaOut], Ctx)](2))

        val unzip = b.add(Unzip[FOut, Ctx]())
        val zipper = b.add(Zip[FViaOut, Ctx]())

        val filterAvailable = Flow[(Option[FOut], Ctx)].collect {
          case (Some(f), ctx) => (f, ctx)
        }

        val filterUnavailable = Flow[(Option[FOut], Ctx)].collect {
          case (None, ctx) => (Option.empty[FViaOut], ctx)
        }

        val mapIntoOption = Flow[(FViaOut, Ctx)].map {
          case (f, ctx) => (Some(f), ctx)
        }

        f ~> broadcast.in

        broadcast.out(0) ~> filterAvailable ~> unzip.in

        unzip.out0 ~> viaF ~> zipper.in0
        unzip.out1 ~> zipper.in1

        zipper.out ~> mapIntoOption ~> merge.in(0)

        broadcast.out(1) ~> filterUnavailable ~> merge.in(1)

        FlowShape(f.in, merge.out)
    }))
}

/**
 * A flow that provides operations which automatically propagate the context of an element.
 * Only a subset of common operations from [[FlowOps]] is supported. As an escape hatch you can
 * use [[FlowWithContextOps.via]] to manually provide the context propagation for otherwise unsupported
 * operations.
 *
 * An "empty" flow can be created by calling `FlowWithContext[Ctx, T]`.
 */
final class FlowWithContext[-In, -CtxIn, +Out, +CtxOut, +Mat](delegate: Flow[(In, CtxIn), (Out, CtxOut), Mat])
    extends GraphDelegate(delegate)
    with FlowWithContextOps[Out, CtxOut, Mat] {
  override type ReprMat[+O, +C, +M] =
    FlowWithContext[In @uncheckedVariance, CtxIn @uncheckedVariance, O, C, M @uncheckedVariance]

  override def via[Out2, Ctx2, Mat2](viaFlow: Graph[FlowShape[(Out, CtxOut), (Out2, Ctx2)], Mat2]): Repr[Out2, Ctx2] =
    new FlowWithContext(delegate.via(viaFlow))

  override def unsafeDataVia[Out2, Mat2](viaFlow: Graph[FlowShape[Out, Out2], Mat2]): Repr[Out2, CtxOut] =
    FlowWithContext.fromTuples(Flow.fromGraph(GraphDSL.createGraph(delegate) { implicit b => d =>
      import GraphDSL.Implicits._

      val unzip = b.add(Unzip[Out, CtxOut]())
      val zipper = b.add(Zip[Out2, CtxOut]())

      d ~> unzip.in

      unzip.out0.via(viaFlow) ~> zipper.in0
      unzip.out1              ~> zipper.in1

      FlowShape(d.in, zipper.out)
    }))

  override def viaMat[Out2, Ctx2, Mat2, Mat3](flow: Graph[FlowShape[(Out, CtxOut), (Out2, Ctx2)], Mat2])(
      combine: (Mat, Mat2) => Mat3): FlowWithContext[In, CtxIn, Out2, Ctx2, Mat3] =
    new FlowWithContext(delegate.viaMat(flow)(combine))

  override def alsoTo(that: Graph[SinkShape[Out], _]): Repr[Out, CtxOut] =
    FlowWithContext.fromTuples(delegate.alsoTo(Sink.contramapImpl(that, (in: (Out, CtxOut)) => in._1)))

  override def alsoToContext(that: Graph[SinkShape[CtxOut], _]): Repr[Out, CtxOut] =
    FlowWithContext.fromTuples(delegate.alsoTo(Sink.contramapImpl(that, (in: (Out, CtxOut)) => in._2)))

  override def wireTap(that: Graph[SinkShape[Out], _]): Repr[Out, CtxOut] =
    FlowWithContext.fromTuples(delegate.wireTap(Sink.contramapImpl(that, (in: (Out, CtxOut)) => in._1)))

  override def wireTapContext(that: Graph[SinkShape[CtxOut], _]): Repr[Out, CtxOut] =
    FlowWithContext.fromTuples(delegate.wireTap(Sink.contramapImpl(that, (in: (Out, CtxOut)) => in._2)))

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.Flow.withAttributes]].
   *
   * @see [[pekko.stream.scaladsl.Flow.withAttributes]]
   */
  override def withAttributes(attr: Attributes): FlowWithContext[In, CtxIn, Out, CtxOut, Mat] =
    new FlowWithContext(delegate.withAttributes(attr))

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.Flow.mapMaterializedValue]].
   *
   * @see [[pekko.stream.scaladsl.Flow.mapMaterializedValue]]
   */
  def mapMaterializedValue[Mat2](f: Mat => Mat2): FlowWithContext[In, CtxIn, Out, CtxOut, Mat2] =
    new FlowWithContext(delegate.mapMaterializedValue(f))

  def asFlow: Flow[(In, CtxIn), (Out, CtxOut), Mat] = delegate

  def asJava[JIn <: In, JCtxIn <: CtxIn, JOut >: Out, JCtxOut >: CtxOut, JMat >: Mat]
      : javadsl.FlowWithContext[JIn, JCtxIn, JOut, JCtxOut, JMat] =
    new javadsl.FlowWithContext(
      javadsl.Flow
        .create[Pair[JIn, JCtxIn]]()
        .map(_.toScala)
        .viaMat(delegate.map {
            case (first, second) =>
              Pair[JOut, JCtxOut](first, second)
          }.asJava, javadsl.Keep.right[NotUsed, JMat]))
}
