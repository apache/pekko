/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import scala.util.Failure
import scala.util.Success

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.dispatch.ExecutionContexts
import pekko.stream.Attributes
import pekko.stream.FlowShape
import pekko.stream.Inlet
import pekko.stream.Outlet
import pekko.stream.SinkShape
import pekko.stream.SubscriptionWithCancelException.NoMoreElementsNeeded
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.Utils.TE

class SubInletOutletSpec extends StreamSpec {

  "SubSinkInlet" should {

    // a contrived custom graph stage just to observe what happens to the SubSinkInlet,
    // it consumes commands from upstream telling it to fail or complete etc. and forwards elements from a side channel
    // downstream through a SubSinkInlet
    class PassAlongSubInStage(sideChannel: Source[String, NotUsed]) extends GraphStage[FlowShape[String, String]] {
      val in = Inlet[String]("in")
      val out = Outlet[String]("out")

      @volatile var subCompletion: AnyRef = _

      override val shape = FlowShape(in, out)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
        val subIn = new SubSinkInlet[String]("subin")
        subIn.setHandler(new InHandler {
          override def onPush(): Unit =
            push(out, subIn.grab())
        })

        override def preStart(): Unit = {
          sideChannel
            .watchTermination() { (_, done) =>
              done.onComplete(c => subCompletion = c)(ExecutionContexts.parasitic)
              NotUsed
            }
            .runWith(Sink.fromGraph(subIn.sink))
          pull(in) // eager pull of commands from upstream as downstream demand goes to subIn
        }

        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit = {
              val cmd = grab(in)
              // we never push to out here
              cmd match {
                case "completeStage" => completeStage()
                case "cancelStage"   => cancelStage(NoMoreElementsNeeded)
                case "failStage"     => failStage(TE("boom"))
                case "closeAll" =>
                  cancel(in)
                  complete(out)
                case _ => // ignore
              }
              if (isAvailable(in))
                pull(in)
            }
          })

        setHandler(out,
          new OutHandler {
            override def onPull(): Unit = {
              if (!subIn.hasBeenPulled)
                subIn.pull()
            }
          })
      }
    }

    class TestSetup {
      val upstream = TestPublisher.probe[String]()
      val sidechannel = TestPublisher.probe[String]()
      val downstream = TestSubscriber.probe[String]()

      val passAlong = new PassAlongSubInStage(Source.fromPublisher(sidechannel))
      Source.fromPublisher(upstream).via(passAlong).runWith(Sink.fromSubscriber(downstream))

    }

    "complete automatically when parent stage completes" in new TestSetup {
      downstream.request(1L)
      sidechannel.expectRequest()
      upstream.expectRequest()
      sidechannel.sendNext("a one")
      downstream.expectNext("a one")
      upstream.sendNext("completeStage")
      awaitAssert(passAlong.subCompletion should equal(Success(Done)))
    }

    "complete automatically when parent stage cancels" in new TestSetup {
      downstream.request(1L)
      sidechannel.expectRequest()
      upstream.expectRequest()
      sidechannel.sendNext("a one")
      downstream.expectNext("a one")
      upstream.sendNext("cancelStage")
      awaitAssert(passAlong.subCompletion should equal(Success(Done)))
    }

    "fail automatically when parent stage fails" in new TestSetup {
      downstream.request(1L)
      sidechannel.expectRequest()
      upstream.expectRequest()
      sidechannel.sendNext("a one")
      downstream.expectNext("a one")
      upstream.sendNext("failStage")
      awaitAssert(passAlong.subCompletion should equal(Failure(TE("boom"))))
    }

    "complete automatically when all parent ins and outs are closed" in new TestSetup {
      downstream.request(1L)
      sidechannel.expectRequest()
      upstream.expectRequest()
      sidechannel.sendNext("a one")
      downstream.expectNext("a one")
      upstream.sendNext("closeAll")
      awaitAssert(passAlong.subCompletion should equal(Success(Done)))
    }

  }

  "SubSourceOutlet" should {

    // a contrived custom sink graph stage just to observe what happens to the SubSourceOutlet when its parent
    // fails/completes
    class ContrivedSubSourceStage extends GraphStage[SinkShape[String]] {
      val in = Inlet[String]("in")

      override val shape = SinkShape(in)

      @volatile var subCompletion: AnyRef = _

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
        val subOut = new SubSourceOutlet[String]("subout")

        override def preStart(): Unit = {
          Source
            .fromGraph(subOut.source)
            .runWith(Sink.ignore)
            .onComplete(t => subCompletion = t)(ExecutionContexts.parasitic)
          subOut.setHandler(new OutHandler {
            override def onPull(): Unit = pull(in)
          })
        }

        setHandler(in,
          new InHandler {
            override def onPush(): Unit = {
              val elem = grab(in)
              elem match {
                case "completeStage" => completeStage()
                case "cancelStage"   => cancelStage(NoMoreElementsNeeded)
                case "failStage"     => failStage(TE("boom"))
                case "completeAll"   => cancel(in)
                case other           => subOut.push(other)
              }
            }
          })
      }
    }

    "complete automatically when parent stage completes" in {
      val stage = new ContrivedSubSourceStage
      Source("element" :: "completeStage" :: Nil).runWith(Sink.fromGraph(stage))
      awaitAssert(stage.subCompletion should equal(Success(Done)))
    }
    "complete automatically when parent stage cancels" in {
      val stage = new ContrivedSubSourceStage
      Source("element" :: "cancelStage" :: Nil).runWith(Sink.fromGraph(stage))
      awaitAssert(stage.subCompletion should equal(Success(Done)))
    }
    "fail automatically when parent stage fails" in {
      val stage = new ContrivedSubSourceStage
      Source("element" :: "failStage" :: Nil).runWith(Sink.fromGraph(stage))
      awaitAssert(stage.subCompletion should equal(Failure(TE("boom"))))
    }
    "cancel automatically when all parent ins and outs are closed" in {
      val stage = new ContrivedSubSourceStage
      Source("element" :: "completeAll" :: Nil).runWith(Sink.fromGraph(stage))
      awaitAssert(stage.subCompletion should equal(Success(Done)))
    }
  }

}
