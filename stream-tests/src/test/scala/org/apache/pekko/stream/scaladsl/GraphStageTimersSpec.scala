/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.stream.Attributes
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.stage.AsyncCallback
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler
import pekko.stream.stage.TimerGraphStageLogic
import pekko.stream.testkit._
import pekko.stream.testkit.Utils._
import pekko.testkit.TestDuration

object GraphStageTimersSpec {
  case object TestSingleTimer
  case object TestSingleTimerResubmit
  case object TestCancelTimer
  case object TestCancelTimerAck
  case object TestRepeatedTimer
  case class Tick(n: Int)

  class SideChannel {
    @volatile var asyncCallback: AsyncCallback[Any] = _
    @volatile var stopPromise: Promise[Option[Nothing]] = _

    def isReady: Boolean = asyncCallback ne null
    def !(msg: Any) = asyncCallback.invoke(msg)

    def stopStage(): Unit = stopPromise.trySuccess(None)
  }

}

class GraphStageTimersSpec extends StreamSpec {
  import GraphStageTimersSpec._

  class TestStage(probe: ActorRef, sideChannel: SideChannel) extends SimpleLinearGraphStage[Int] {
    override def createLogic(inheritedAttributes: Attributes) = new TimerGraphStageLogic(shape) {
      val tickCount = Iterator.from(1)

      setHandler(in,
        new InHandler {
          override def onPush() = push(out, grab(in))
        })

      setHandler(out,
        new OutHandler {
          override def onPull(): Unit = pull(in)
        })

      override def preStart() =
        sideChannel.asyncCallback = getAsyncCallback(onTestEvent)

      override protected def onTimer(timerKey: Any): Unit = {
        val tick = Tick(tickCount.next())
        probe ! tick
        if (timerKey == "TestSingleTimerResubmit" && tick.n == 1)
          scheduleOnce("TestSingleTimerResubmit", 500.millis.dilated)
        else if (timerKey == "TestRepeatedTimer" && tick.n == 5)
          cancelTimer("TestRepeatedTimer")
      }

      private def onTestEvent(event: Any): Unit = event match {
        case TestSingleTimer =>
          scheduleOnce("TestSingleTimer", 500.millis.dilated)
        case TestSingleTimerResubmit =>
          scheduleOnce("TestSingleTimerResubmit", 500.millis.dilated)
        case TestCancelTimer =>
          scheduleOnce("TestCancelTimer", 1.milli.dilated)
          // Likely in mailbox but we cannot guarantee
          cancelTimer("TestCancelTimer")
          probe ! TestCancelTimerAck
          scheduleOnce("TestCancelTimer", 500.milli.dilated)
        case TestRepeatedTimer =>
          scheduleWithFixedDelay("TestRepeatedTimer", 100.millis.dilated, 100.millis.dilated)
        case unexpected => throw new RuntimeException(s"Unexpected: $unexpected")
      }
    }
  }

  "GraphStage timer support" must {

    def setupIsolatedStage: SideChannel = {
      val channel = new SideChannel
      val stopPromise = Source.maybe[Nothing].via(new TestStage(testActor, channel)).to(Sink.ignore).run()
      channel.stopPromise = stopPromise
      awaitCond(channel.isReady)
      channel
    }

    "receive single-shot timer" in {
      val driver = setupIsolatedStage
      within(2.seconds) {
        within(500.millis, 1.second) {
          driver ! TestSingleTimer
          expectMsg(Tick(1))
        }
        expectNoMessage(1.second)
      }

      driver.stopStage()
    }

    "resubmit single-shot timer" in {
      val driver = setupIsolatedStage

      within(2.5.seconds) {
        within(500.millis, 1.second) {
          driver ! TestSingleTimerResubmit
          expectMsg(Tick(1))
        }
        within(1.second) {
          expectMsg(Tick(2))
        }
        expectNoMessage(1.second)
      }

      driver.stopStage()
    }

    "correctly cancel a named timer" in {
      val driver = setupIsolatedStage

      driver ! TestCancelTimer
      within(500.millis) {
        expectMsg(TestCancelTimerAck)
      }
      within(300.millis, 1.second) {
        expectMsg(Tick(1))
      }
      expectNoMessage(1.second)

      driver.stopStage()
    }

    "receive and cancel a repeated timer" in {
      val driver = setupIsolatedStage

      driver ! TestRepeatedTimer
      val seq = receiveWhile(2.seconds) {
        case t: Tick => t
      }
      (seq should have).length(5)
      expectNoMessage(1.second)

      driver.stopStage()
    }

    class TestStage2 extends SimpleLinearGraphStage[Int] {
      override def createLogic(inheritedAttributes: Attributes) = new TimerGraphStageLogic(shape) {
        var tickCount = 0

        override def preStart(): Unit = scheduleWithFixedDelay("tick", 100.millis, 100.millis)

        setHandler(out,
          new OutHandler {
            override def onPull() = () // Do nothing
            override def onDownstreamFinish(cause: Throwable) = completeStage()
          })

        setHandler(in,
          new InHandler {
            override def onPush() = () // Do nothing
            override def onUpstreamFinish() = completeStage()
            override def onUpstreamFailure(ex: Throwable) = failStage(ex)
          })

        override def onTimer(timerKey: Any) = {
          tickCount += 1
          if (isAvailable(out)) push(out, tickCount)
          if (tickCount == 3) cancelTimer("tick")
        }
      }
    }

    "produce scheduled ticks as expected" in {
      val upstream = TestPublisher.probe[Int]()
      val downstream = TestSubscriber.probe[Int]()

      Source.fromPublisher(upstream).via(new TestStage2).runWith(Sink.fromSubscriber(downstream))

      downstream.request(5)
      downstream.expectNext(1)
      downstream.expectNext(2)
      downstream.expectNext(3)

      downstream.expectNoMessage(1.second)

      upstream.sendComplete()
      downstream.expectComplete()
    }

    "propagate error if onTimer throws an exception" in {
      val exception = TE("Expected exception to the rule")
      val upstream = TestPublisher.probe[Int]()
      val downstream = TestSubscriber.probe[Int]()

      Source
        .fromPublisher(upstream)
        .via(new SimpleLinearGraphStage[Int] {
          override def createLogic(inheritedAttributes: Attributes) = new TimerGraphStageLogic(shape) {
            override def preStart(): Unit = scheduleOnce("tick", 100.millis)

            setHandler(in,
              new InHandler {
                override def onPush() = () // Ignore
              })

            setHandler(out,
              new OutHandler {
                override def onPull(): Unit = pull(in)
              })

            override def onTimer(timerKey: Any) = throw exception
          }
        })
        .runWith(Sink.fromSubscriber(downstream))

      downstream.request(1)
      downstream.expectError(exception)
    }

  }

}
