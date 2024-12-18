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
import pekko.stream._

object SourceWithContext {

  /**
   * Creates a SourceWithContext from a regular source that operates on a tuple of `(data, context)` elements.
   */
  def fromTuples[Out, CtxOut, Mat](source: Source[(Out, CtxOut), Mat]): SourceWithContext[Out, CtxOut, Mat] =
    new SourceWithContext(source)

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
   *         is contained within an Option which indicates whether the original source's element had viaFlow
   *         applied.
   * @since 1.1.0
   */
  @ApiMayChange
  def unsafeOptionalDataVia[SOut, FOut, Ctx, SMat, FMat, Mat](source: SourceWithContext[Option[SOut], Ctx, SMat],
      viaFlow: Flow[SOut, FOut, FMat])(
      combine: (SMat, FMat) => Mat
  ): SourceWithContext[Option[FOut], Ctx, Mat] =
    SourceWithContext.fromTuples(Source.fromGraph(GraphDSL.createGraph(source, viaFlow)(combine) {
      implicit b => (s, viaF) =>
        import GraphDSL.Implicits._

        case class IndexedCtx(idx: Long, ctx: Ctx)
        val partition = b.add(Partition[(Option[SOut], IndexedCtx)](2,
          {
            case (None, _)    => 0
            case (Some(_), _) => 1
          }))

        val sequence = Flow[(Option[SOut], Ctx)].zipWithIndex
          .map {
            case ((opt, ctx), idx) => (opt, IndexedCtx(idx, ctx))
          }

        val unzip = b.add(Unzip[Option[SOut], IndexedCtx]())
        val zipper = b.add(Zip[FOut, IndexedCtx]())
        val mergeSequence = b.add(MergeSequence[(Option[FOut], IndexedCtx)](2)(_._2.idx))
        val unwrapSome = b.add(Flow[Option[SOut]].map {
          case Some(elem) => elem
          case _          => throw new IllegalStateException("Only expects Some")
        })
        val unwrap = Flow[(Option[FOut], IndexedCtx)].map {
          case (opt, indexedCtx) => (opt, indexedCtx.ctx)
        }

        val mapIntoOption = Flow[(FOut, IndexedCtx)].map {
          case (elem, indexedCtx) => (Some(elem), indexedCtx)
        }

        //format: off
        s ~> sequence ~> partition.in
        partition.out(0).asInstanceOf[Outlet[(Option[FOut], IndexedCtx)]] ~> mergeSequence.in(0)
        partition.out(1) ~> unzip.in
                            unzip.out0 ~> unwrapSome ~> viaF ~> zipper.in0
                            unzip.out1                       ~> zipper.in1
                                                                zipper.out ~> mapIntoOption ~> mergeSequence.in(1)

        //format: on
        SourceShape((mergeSequence.out ~> unwrap).outlet)
    }))
}

/**
 * A source that provides operations which automatically propagate the context of an element.
 * Only a subset of common operations from [[FlowOps]] is supported. As an escape hatch you can
 * use [[FlowWithContextOps.via]] to manually provide the context propagation for otherwise unsupported
 * operations.
 *
 * Can be created by calling [[Source.asSourceWithContext]]
 */
final class SourceWithContext[+Out, +Ctx, +Mat] private[stream] (delegate: Source[(Out, Ctx), Mat])
    extends GraphDelegate(delegate)
    with FlowWithContextOps[Out, Ctx, Mat] {
  override type ReprMat[+O, +C, +M] = SourceWithContext[O, C, M @uncheckedVariance]

  override def via[Out2, Ctx2, Mat2](viaFlow: Graph[FlowShape[(Out, Ctx), (Out2, Ctx2)], Mat2]): Repr[Out2, Ctx2] =
    new SourceWithContext(delegate.via(viaFlow))

  override def unsafeDataVia[Out2, Mat2](viaFlow: Graph[FlowShape[Out, Out2], Mat2]): Repr[Out2, Ctx] =
    SourceWithContext.fromTuples(Source.fromGraph(GraphDSL.createGraph(delegate) { implicit b => d =>
      import GraphDSL.Implicits._

      val unzip = b.add(Unzip[Out, Ctx]())
      val zipper = b.add(Zip[Out2, Ctx]())

      d ~> unzip.in

      unzip.out0.via(viaFlow) ~> zipper.in0
      unzip.out1              ~> zipper.in1

      SourceShape(zipper.out)
    }))

  override def viaMat[Out2, Ctx2, Mat2, Mat3](flow: Graph[FlowShape[(Out, Ctx), (Out2, Ctx2)], Mat2])(
      combine: (Mat, Mat2) => Mat3): SourceWithContext[Out2, Ctx2, Mat3] =
    new SourceWithContext(delegate.viaMat(flow)(combine))

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.Source.withAttributes]].
   *
   * @see [[pekko.stream.scaladsl.Source.withAttributes]]
   */
  override def withAttributes(attr: Attributes): SourceWithContext[Out, Ctx, Mat] =
    new SourceWithContext(delegate.withAttributes(attr))

  /**
   * Context-preserving variant of [[pekko.stream.scaladsl.Source.mapMaterializedValue]].
   *
   * @see [[pekko.stream.scaladsl.Source.mapMaterializedValue]]
   */
  def mapMaterializedValue[Mat2](f: Mat => Mat2): SourceWithContext[Out, Ctx, Mat2] =
    new SourceWithContext(delegate.mapMaterializedValue(f))

  /**
   * Connect this [[pekko.stream.scaladsl.SourceWithContext]] to a [[pekko.stream.scaladsl.Sink]],
   * concatenating the processing steps of both.
   */
  def to[Mat2](sink: Graph[SinkShape[(Out, Ctx)], Mat2]): RunnableGraph[Mat] =
    delegate.toMat(sink)(Keep.left)

  /**
   * Connect this [[pekko.stream.scaladsl.SourceWithContext]] to a [[pekko.stream.scaladsl.Sink]],
   * concatenating the processing steps of both.
   */
  def toMat[Mat2, Mat3](sink: Graph[SinkShape[(Out, Ctx)], Mat2])(combine: (Mat, Mat2) => Mat3): RunnableGraph[Mat3] =
    delegate.toMat(sink)(combine)

  override def alsoTo(that: Graph[SinkShape[Out], _]): Repr[Out, Ctx] =
    SourceWithContext.fromTuples(delegate.alsoTo(Sink.contramapImpl(that, (in: (Out, Ctx)) => in._1)))

  override def alsoToContext(that: Graph[SinkShape[Ctx], _]): Repr[Out, Ctx] =
    SourceWithContext.fromTuples(delegate.alsoTo(Sink.contramapImpl(that, (in: (Out, Ctx)) => in._2)))

  override def wireTap(that: Graph[SinkShape[Out], _]): Repr[Out, Ctx] =
    SourceWithContext.fromTuples(delegate.wireTap(Sink.contramapImpl(that, (in: (Out, Ctx)) => in._1)))

  override def wireTapContext(that: Graph[SinkShape[Ctx], _]): Repr[Out, Ctx] =
    SourceWithContext.fromTuples(delegate.wireTap(Sink.contramapImpl(that, (in: (Out, Ctx)) => in._2)))

  /**
   * Connect this [[pekko.stream.scaladsl.SourceWithContext]] to a [[pekko.stream.scaladsl.Sink]] and run it.
   * The returned value is the materialized value of the `Sink`.
   *
   * Note that the `ActorSystem` can be used as the implicit `materializer` parameter to use the
   * [[pekko.stream.SystemMaterializer]] for running the stream.
   */
  def runWith[Mat2](sink: Graph[SinkShape[(Out, Ctx)], Mat2])(implicit materializer: Materializer): Mat2 =
    delegate.runWith(sink)

  /**
   * Stops automatic context propagation from here and converts this to a regular
   * stream of a pair of (data, context).
   */
  def asSource: Source[(Out, Ctx), Mat] = delegate

  def asJava[JOut >: Out, JCtx >: Ctx, JMat >: Mat]: javadsl.SourceWithContext[JOut, JCtx, JMat] =
    new javadsl.SourceWithContext(this)
}
