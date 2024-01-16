/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.testkit

import scala.annotation.nowarn

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorRefFactory
import pekko.actor.ActorSystem
import pekko.stream.ActorMaterializer
import pekko.stream.ActorMaterializerSettings
import pekko.stream.Materializer
import pekko.stream.scaladsl._

import org.reactivestreams.Publisher

class ChainSetup[In, Out, M](
    stream: Flow[In, In, NotUsed] => Flow[In, Out, M],
    val settings: ActorMaterializerSettings,
    materializer: Materializer,
    toPublisher: (Source[Out, _], Materializer) => Publisher[Out])(implicit val system: ActorSystem) {

  @nowarn("msg=deprecated")
  def this(
      stream: Flow[In, In, NotUsed] => Flow[In, Out, M],
      settings: ActorMaterializerSettings,
      toPublisher: (Source[Out, _], Materializer) => Publisher[Out])(implicit system: ActorSystem) =
    this(stream, settings, ActorMaterializer(settings)(system), toPublisher)(system)

  @nowarn("msg=deprecated")
  def this(
      stream: Flow[In, In, NotUsed] => Flow[In, Out, M],
      settings: ActorMaterializerSettings,
      materializerCreator: (ActorMaterializerSettings, ActorRefFactory) => Materializer,
      toPublisher: (Source[Out, _], Materializer) => Publisher[Out])(implicit system: ActorSystem) =
    this(stream, settings, materializerCreator(settings, system), toPublisher)(system)

  val upstream = TestPublisher.manualProbe[In]()
  val downstream = TestSubscriber.probe[Out]()
  private val s = Source.fromPublisher(upstream).via(stream(Flow[In].map(x => x).named("buh")))
  val publisher = toPublisher(s, materializer)
  val upstreamSubscription = upstream.expectSubscription()
  publisher.subscribe(downstream)
  val downstreamSubscription = downstream.expectSubscription()
}
