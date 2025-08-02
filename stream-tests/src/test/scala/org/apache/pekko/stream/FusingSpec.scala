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

import scala.concurrent.{ duration, Await, Promise }

import duration._

import org.apache.pekko
import pekko.Done
import pekko.stream.impl.UnfoldResourceSource
import pekko.stream.impl.fusing.GraphInterpreter
import pekko.stream.scaladsl._
import pekko.stream.stage.GraphStage
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils.TE

class FusingSpec extends StreamSpec {

  def actorRunningStage = {
    GraphInterpreter.currentInterpreter.context
  }

  val snitchFlow = Flow[Int].map(x => { testActor ! actorRunningStage; x }).async

  "SubFusingActorMaterializer" must {

    "work with asynchronous boundaries in the subflows" in {
      val async = Flow[Int].map(_ * 2).async
      Source(0 to 9)
        .map(_ * 10)
        .flatMapMerge(5, i => Source(i to (i + 9)).via(async))
        .grouped(1000)
        .runWith(Sink.head)
        .futureValue
        .sorted should ===(0 to 198 by 2)
    }

    "use multiple actors when there are asynchronous boundaries in the subflows (manual)" in {
      val async = Flow[Int].map(x => { testActor ! actorRunningStage; x }).async
      Source(0 to 9)
        .via(snitchFlow.async)
        .flatMapMerge(5, i => Source.single(i).via(async))
        .grouped(1000)
        .runWith(Sink.head)
        .futureValue
        .sorted should ===(0 to 9)
      val refs = receiveN(20)
      refs.toSet should have size 11 // main flow + 10 subflows
    }

    "use multiple actors when there are asynchronous boundaries in the subflows (operator)" in {
      Source(0 to 9)
        .via(snitchFlow)
        .flatMapMerge(5, i => Source.single(i).via(snitchFlow.async))
        .grouped(1000)
        .runWith(Sink.head)
        .futureValue
        .sorted should ===(0 to 9)
      val refs = receiveN(20)
      refs.toSet should have size 11 // main flow + 10 subflows
    }

    "use one actor per grouped substream when there is an async boundary around the flow (manual)" in {
      val in = 0 to 9
      Source(in)
        .via(snitchFlow)
        .groupBy(in.size, identity)
        .via(snitchFlow.async)
        .mergeSubstreams
        .runWith(Sink.seq)
        .futureValue
        .sorted should ===(in)
      val refs = receiveN(in.size + in.size) // each element through the first map, then the second map

      refs.toSet should have size (in.size + 1) // outer/main actor + 1 actor per subflow
    }

    "use one actor per grouped substream when there is an async boundary around the flow (operator)" in {
      val in = 0 to 9
      Source(in)
        .via(snitchFlow)
        .groupBy(in.size, identity)
        .via(snitchFlow)
        .async
        .mergeSubstreams
        .runWith(Sink.seq)
        .futureValue
        .sorted should ===(in)
      val refs = receiveN(in.size + in.size) // each element through the first map, then the second map
      refs.toSet should have size (in.size + 1) // outer/main actor + 1 actor per subflow
    }

    // an UnfoldResourceSource equivalent without an async boundary
    case class UnfoldResourceNoAsyncBoundary[R, T](create: () => R, readData: (R) => Option[T], close: (R) => Unit)
        extends GraphStage[SourceShape[T]] {
      val stage_ = new UnfoldResourceSource(create, readData, close)
      override def initialAttributes: Attributes = Attributes.none
      override val shape = stage_.shape
      def createLogic(inheritedAttributes: Attributes) = stage_.createLogic(inheritedAttributes)
      def asSource = Source.fromGraph(this)
    }

    "propagate downstream errors through async boundary" in {
      val promise = Promise[Done]()
      val slowInitSrc = UnfoldResourceNoAsyncBoundary(
        () => { Await.result(promise.future, 1.minute); () },
        (_: Unit) => Some(1),
        (_: Unit) => ()).asSource.watchTermination()(Keep.right).async // commenting this out, makes the test pass
      val downstream = Flow[Int]
        .prepend(Source.single(1))
        .flatMapPrefix(0) {
          case Nil        => throw TE("I hate mondays")
          case unexpected => throw new RuntimeException(s"Unexpected: $unexpected")
        }
        .watchTermination()(Keep.right)
        .to(Sink.ignore)

      val g = slowInitSrc.toMat(downstream)(Keep.both)

      val (f1, f2) = g.run()
      f2.failed.futureValue shouldEqual TE("I hate mondays")
      f1.value should be(empty)
      // by now downstream managed to fail, hence it already processed the message from Flow.single,
      // hence we know for sure that all graph stage locics in the downstream interpreter were initialized(=preStart)
      // hence upstream subscription was initiated.
      // since we're still blocking upstream's preStart we know for sure it didn't respond to the subscription request
      // since a blocked actor can not process additional messages from its inbox.
      // so long story short: downstream was able to initialize, subscribe and fail before upstream responded to the subscription request.
      // prior to akka#29194, this scenario resulted with cancellation signal rather than the expected error signal.
      promise.success(Done)
      f1.failed.futureValue shouldEqual TE("I hate mondays")
    }

    "propagate 'parallel' errors through async boundary via a common downstream" in {
      val promise = Promise[Done]()
      val slowInitSrc = UnfoldResourceNoAsyncBoundary(
        () => { Await.result(promise.future, 1.minute); () },
        (_: Unit) => Some(1),
        (_: Unit) => ()).asSource.watchTermination()(Keep.right).async // commenting this out, makes the test pass

      val failingSrc = Source.failed(TE("I hate mondays")).watchTermination()(Keep.right)

      val g = slowInitSrc.zipMat(failingSrc)(Keep.both).to(Sink.ignore)

      val (f1, f2) = g.run()
      f2.failed.futureValue shouldEqual TE("I hate mondays")
      f1.value should be(empty)
      // by now downstream managed to fail, hence it already processed the message from Flow.single,
      // hence we know for sure that all graph stage locics in the downstream interpreter were initialized(=preStart)
      // hence upstream subscription was initiated.
      // since we're still blocking upstream's preStart we know for sure it didn't respond to the subscription request
      // since a blocked actor can not process additional messages from its inbox.
      // so long story short: downstream was able to initialize, subscribe and fail before upstream responded to the subscription request.
      // prior to akka#29194, this scenario resulted with cancellation signal rather than the expected error signal.
      promise.success(Done)
      f1.failed.futureValue shouldEqual TE("I hate mondays")
    }

  }

}
