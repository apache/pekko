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

package org.apache.pekko.stream

import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import org.apache.pekko

package object javadsl {

  /** Converts a `CompletionStage` of any subtype of `T`, as returned by widened javadsl functions. */
  private[javadsl] def stageToScala[T](stage: CompletionStage[? <: T]): Future[T] = stage.asScala

  /** Converts an `Optional` of any subtype of `T`, as returned by widened javadsl functions. */
  private[javadsl] def optionalToScala[T](optional: Optional[? <: T]): Option[T] = optional.toScala

  /** Converts a `java.lang.Iterable` of any subtype of `T`, as returned by widened javadsl functions. */
  private[javadsl] def iterableToScala[T](iterable: java.lang.Iterable[? <: T]): Iterable[T] = iterable.asScala

  // Scala 3 does not treat e.g. SourceShape[? <: T] as conforming to SourceShape[T] even though the shapes
  // are variant, so the widened javadsl graph parameters are narrowed back before delegating to scaladsl.
  // The casts are safe: shapes only change the static view of the element type and erasure is identical.

  private[javadsl] def sourceGraph[T, M](graph: Graph[? <: SourceShape[? <: T], M]): Graph[SourceShape[T], M] =
    graph.asInstanceOf[Graph[SourceShape[T], M]]

  private[javadsl] def sinkGraph[T, M](graph: Graph[? <: SinkShape[? >: T], M]): Graph[SinkShape[T], M] =
    graph.asInstanceOf[Graph[SinkShape[T], M]]

  private[javadsl] def flowGraph[I, O, M](graph: Graph[? <: FlowShape[? >: I, ? <: O], M]): Graph[FlowShape[I, O], M] =
    graph.asInstanceOf[Graph[FlowShape[I, O], M]]

  def combinerToScala[M1, M2, M](f: pekko.japi.function.Function2[? >: M1, ? >: M2, ? <: M]): (M1, M2) => M =
    f match {
      case x if x eq Keep.left   => scaladsl.Keep.left.asInstanceOf[(M1, M2) => M]
      case x if x eq Keep.right  => scaladsl.Keep.right.asInstanceOf[(M1, M2) => M]
      case s: Function2[?, ?, ?] => s.asInstanceOf[(M1, M2) => M]
      case other                 => other.apply _
    }
}
