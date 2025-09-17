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

import scala.annotation.unchecked.uncheckedVariance

import org.reactivestreams._

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.{ DoNotInherit, InternalApi }
import pekko.event.Logging
import pekko.stream._
import pekko.stream.impl.StreamLayout.AtomicModule

/**
 * INTERNAL API
 */
@DoNotInherit private[pekko] abstract class SourceModule[+Out, +Mat](val shape: SourceShape[Out])
    extends AtomicModule[SourceShape[Out], Mat] {

  protected def label: String = Logging.simpleName(this)
  final override def toString: String = f"$label [${System.identityHashCode(this)}%08x]"

  def create(context: MaterializationContext): (Publisher[Out] @uncheckedVariance, Mat)

  def attributes: Attributes

  // TODO: Amendshape changed the name of ports. Is it needed anymore?
  protected def amendShape(attr: Attributes): SourceShape[Out] = {
    val thisN = traversalBuilder.attributes.nameOrDefault(null)
    val thatN = attr.nameOrDefault(null)

    if ((thatN eq null) || thisN == thatN) shape
    else shape.copy(out = Outlet(thatN + ".out"))
  }

  override private[stream] def traversalBuilder =
    LinearTraversalBuilder.fromModule(this, attributes).makeIsland(SourceModuleIslandTag)

}

/**
 * INTERNAL API
 * Holds a `Subscriber` representing the input side of the flow.
 * The `Subscriber` can later be connected to an upstream `Publisher`.
 */
@InternalApi private[pekko] final class SubscriberSource[Out](val attributes: Attributes, shape: SourceShape[Out])
    extends SourceModule[Out, Subscriber[Out]](shape) {

  override def create(context: MaterializationContext): (Publisher[Out], Subscriber[Out]) = {
    val processor = new VirtualProcessor[Out]
    (processor, processor)
  }

  override def withAttributes(attr: Attributes): SourceModule[Out, Subscriber[Out]] =
    new SubscriberSource[Out](attr, amendShape(attr))
}

/**
 * INTERNAL API
 * Construct a transformation starting with given publisher. The transformation steps
 * are executed by a series of [[org.reactivestreams.Processor]] instances
 * that mediate the flow of elements downstream and the propagation of
 * back-pressure upstream.
 */
@InternalApi private[pekko] final class PublisherSource[Out](
    p: Publisher[Out],
    val attributes: Attributes,
    shape: SourceShape[Out])
    extends SourceModule[Out, NotUsed](shape) {

  override protected def label: String = s"PublisherSource($p)"

  override def create(context: MaterializationContext) = (p, NotUsed)

  override def withAttributes(attr: Attributes): SourceModule[Out, NotUsed] =
    new PublisherSource[Out](p, attr, amendShape(attr))
}
