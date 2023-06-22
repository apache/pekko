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

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.Kill
import pekko.actor.NoSerializationVerificationNeeded
import pekko.actor.PoisonPill
import pekko.event.Logging
import pekko.stream._
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.GraphStageWithMaterializedValue
import pekko.stream.stage.InHandler
import pekko.stream.testkit.StreamSpec
import pekko.testkit.EventFilter
import pekko.testkit.ImplicitSender
import pekko.testkit.TestEvent
import pekko.testkit.TestProbe

class StageActorRefSpec extends StreamSpec with ImplicitSender {
  import StageActorRefSpec._
  import StageActorRefSpec.ControlProtocol._

  def sumStage(probe: ActorRef) = SumTestStage(probe)

  "A Graph Stage's ActorRef" must {

    "receive messages" in {
      val (_, res) = Source.maybe[Int].toMat(sumStage(testActor))(Keep.both).run()

      val stageRef = expectMsgType[ActorRef]
      stageRef ! Add(1)
      stageRef ! Add(2)
      stageRef ! Add(3)
      stageRef ! StopNow

      res.futureValue should ===(6)
    }

    "be able to be replied to" in {
      val (_, res) = Source.maybe[Int].toMat(sumStage(testActor))(Keep.both).run()

      val stageRef = expectMsgType[ActorRef]
      stageRef ! AddAndTell(1)
      expectMsg(1)
      stageRef should ===(lastSender)
      lastSender ! AddAndTell(9)
      expectMsg(10)

      stageRef ! StopNow
      res.futureValue should ===(10)
    }

    "yield the same 'self' ref each time" in {
      val (_, res) = Source.maybe[Int].toMat(sumStage(testActor))(Keep.both).run()

      val stageRef = expectMsgType[ActorRef]
      stageRef ! CallInitStageActorRef
      val explicitlyObtained = expectMsgType[ActorRef]
      stageRef should ===(explicitlyObtained)
      explicitlyObtained ! AddAndTell(1)
      expectMsg(1)
      lastSender ! AddAndTell(2)
      expectMsg(3)
      stageRef ! AddAndTell(3)
      expectMsg(6)

      stageRef ! StopNow
      res.futureValue should ===(6)
    }

    "be watchable" in {
      val (source, res) = Source.maybe[Int].toMat(sumStage(testActor))(Keep.both).run()

      val stageRef = expectMsgType[ActorRef]
      watch(stageRef)

      stageRef ! AddAndTell(1)
      expectMsg(1)
      source.success(None)

      res.futureValue should ===(1)
      expectTerminated(stageRef)
    }

    "be able to become" in {
      val (source, res) = Source.maybe[Int].toMat(sumStage(testActor))(Keep.both).run()

      val stageRef = expectMsgType[ActorRef]
      watch(stageRef)

      stageRef ! Add(1)

      stageRef ! BecomeStringEcho
      stageRef ! 42
      expectMsg("42")

      source.success(None)
      res.futureValue should ===(1)
      expectTerminated(stageRef)
    }

    "reply Terminated when terminated stage is watched" in {
      val (source, res) = Source.maybe[Int].toMat(sumStage(testActor))(Keep.both).run()

      val stageRef = expectMsgType[ActorRef]
      watch(stageRef)

      stageRef ! AddAndTell(1)
      expectMsg(1)
      source.success(None)

      res.futureValue should ===(1)
      expectTerminated(stageRef)

      val p = TestProbe()
      p.watch(stageRef)
      p.expectTerminated(stageRef)
    }

    "be un-watchable" in {
      val (source, res) = Source.maybe[Int].toMat(sumStage(testActor))(Keep.both).run()

      val stageRef = expectMsgType[ActorRef]
      watch(stageRef)
      unwatch(stageRef)

      stageRef ! AddAndTell(1)
      expectMsg(1)
      source.success(None)

      res.futureValue should ===(1)
      expectNoMessage(100.millis)
    }

    "ignore and log warnings for PoisonPill and Kill messages" in {
      val (source, res) = Source.maybe[Int].toMat(sumStage(testActor))(Keep.both).run()

      val stageRef = expectMsgType[ActorRef]
      stageRef ! AddAndTell(40)
      expectMsg(40)

      val filter = EventFilter.custom {
        case _: Logging.Warning => true
        case _                  => false
      }
      system.eventStream.publish(TestEvent.Mute(filter))
      system.eventStream.subscribe(testActor, classOf[Logging.Warning])

      stageRef ! PoisonPill // should log a warning, and NOT stop the stage.
      val actorName = """StageActorRef-[\d+]"""
      val expectedMsg =
        s"[PoisonPill|Kill] message sent to StageActorRef($actorName) will be ignored,since it is not a real Actor. " +
        "Use a custom message type to communicate with it instead."
      expectMsgPF(1.second, expectedMsg) {
        case Logging.Warning(_, _, msg) => expectedMsg.r.pattern.matcher(msg.toString).matches()
      }

      stageRef ! Kill // should log a warning, and NOT stop the stage.
      expectMsgPF(1.second, expectedMsg) {
        case Logging.Warning(_, _, msg) => expectedMsg.r.pattern.matcher(msg.toString).matches()
      }

      source.success(Some(2))
      res.futureValue should ===(42)
    }

  }

}

object StageActorRefSpec {

  object ControlProtocol {
    case class Add(n: Int) extends NoSerializationVerificationNeeded
    case class AddAndTell(n: Int) extends NoSerializationVerificationNeeded
    case object CallInitStageActorRef extends NoSerializationVerificationNeeded
    case object BecomeStringEcho extends NoSerializationVerificationNeeded
    case object PullNow extends NoSerializationVerificationNeeded
    case object StopNow extends NoSerializationVerificationNeeded
  }

  import ControlProtocol._

  case class SumTestStage(probe: ActorRef) extends GraphStageWithMaterializedValue[SinkShape[Int], Future[Int]] {
    val in = Inlet[Int]("IntSum.in")
    override val shape: SinkShape[Int] = SinkShape.of(in)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val p: Promise[Int] = Promise()

      val logic = new GraphStageLogic(shape) {
        implicit def self: ActorRef = stageActor.ref // must be a `def`; we want self to be the sender for our replies
        var sum: Int = 0

        override def preStart(): Unit = {
          pull(in)
          probe ! getStageActor(behavior).ref
        }

        def behavior(m: (ActorRef, Any)): Unit = {
          m match {
            case (_, Add(n))                     => sum += n
            case (_, PullNow)                    => pull(in)
            case (sender, CallInitStageActorRef) => sender ! getStageActor(behavior).ref
            case (_, BecomeStringEcho) =>
              getStageActor {
                case (theSender, msg) => theSender ! msg.toString
              }
            case (_, StopNow) =>
              p.trySuccess(sum)
              completeStage()
            case (sender, AddAndTell(n)) =>
              sum += n
              sender ! sum
            case _ => throw new RuntimeException("unexpected: " + m)
          }
        }

        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit = {
              sum += grab(in)
              p.trySuccess(sum)
              completeStage()
            }

            override def onUpstreamFinish(): Unit = {
              p.trySuccess(sum)
              completeStage()
            }

            override def onUpstreamFailure(ex: Throwable): Unit = {
              p.tryFailure(ex)
              failStage(ex)
            }
          })
      }

      logic -> p.future
    }
  }

}
