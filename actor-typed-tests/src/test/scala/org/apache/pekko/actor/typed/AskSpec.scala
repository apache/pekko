/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.util.Success
import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.LoggingTestKit
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.internal.adapter.ActorSystemAdapter
import pekko.actor.typed.scaladsl.AskPattern._
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.Behaviors._
import pekko.pattern.StatusReply
import pekko.testkit.TestException
import pekko.util.Timeout

object AskSpec {
  sealed trait Msg
  final case class Foo(s: String, replyTo: ActorRef[String]) extends Msg
  final case class Stop(replyTo: ActorRef[Unit]) extends Msg
}

class AskSpec extends ScalaTestWithActorTestKit("""
    pekko.loglevel=DEBUG
    pekko.actor.debug.event-stream = on
    """) with AnyWordSpecLike with LogCapturing {

  import AskSpec._

  implicit def executor: ExecutionContext =
    system.executionContext

  val behavior: Behavior[Msg] = receive[Msg] {
    case (_, foo: Foo) =>
      foo.replyTo ! "foo"
      Behaviors.same
    case (_, Stop(r)) =>
      r ! (())
      Behaviors.stopped
  }

  "Ask pattern" must {
    "fail the future and publish deadletter with recipient if the actor is already terminated" in {
      import pekko.actor.typed.internal.adapter.ActorRefAdapter._

      val ref = spawn(behavior)
      val stopResult: Future[Unit] = ref.ask(Stop.apply)
      stopResult.futureValue

      val probe = createTestProbe()
      probe.expectTerminated(ref, probe.remainingOrDefault)
      val answer: Future[String] = ref.ask(Foo("bar", _))
      val result = answer.failed.futureValue
      result shouldBe a[TimeoutException]
      result.getMessage should include("had already been terminated.")

      val deadLetterProbe = createDeadLetterProbe()
      val deadLetter = deadLetterProbe.receiveMessage()
      deadLetter.message match {
        case Foo(s, _) => s should ===("bar")
        case _         => fail(s"unexpected DeadLetter: $deadLetter")
      }

      val deadLettersRef = system.classicSystem.deadLetters
      deadLetter.recipient shouldNot equal(deadLettersRef)
      deadLetter.recipient should equal(toClassic(ref))
    }

    "succeed when the actor is alive" in {
      val ref = spawn(behavior)
      val response: Future[String] = ref.ask(Foo("bar", _))
      response.futureValue should ===("foo")
    }

    "provide a symbolic alias that works the same" in {
      val ref = spawn(behavior)
      val response: Future[String] = ref ? (Foo("bar", _))
      response.futureValue should ===("foo")
    }

    "fail the future if the actor doesn't reply in time" in {
      val unhandledProbe = createUnhandledMessageProbe()

      val actor = spawn(Behaviors.empty[Foo])
      implicit val timeout: Timeout = 10.millis

      val answer: Future[String] = actor.ask(Foo("bar", _))
      unhandledProbe.receiveMessage()
      val result = answer.failed.futureValue
      result shouldBe a[TimeoutException]
      result.getMessage should startWith("Ask timed out on")
    }

    "fail the future if the actor doesn't exist" in {
      val noSuchActor: ActorRef[Msg] = system match {
        case adaptedSys: ActorSystemAdapter[_] =>
          import pekko.actor.typed.scaladsl.adapter._
          adaptedSys.system.provider.resolveActorRef("/foo/bar")
        case _ =>
          fail("this test must only run in an adapted actor system")
      }

      val deadLetterProbe = createDeadLetterProbe()

      val answer: Future[String] = noSuchActor.ask(Foo("bar", _))
      val result = answer.failed.futureValue
      result shouldBe a[TimeoutException]
      result.getMessage should include("had already been terminated")

      val deadLetter = deadLetterProbe.receiveMessage()
      deadLetter.message match {
        case Foo(s, _) => s should ===("bar")
        case _         => fail(s"unexpected DeadLetter: $deadLetter")
      }
    }

    "transform a replied org.apache.pekko.actor.Status.Failure to a failed future" in {
      // It's unlikely but possible that this happens, since the receiving actor would
      // have to accept a message with an actoref that accepts AnyRef or be doing crazy casting
      // For completeness sake though
      implicit val classicSystem = pekko.actor.ActorSystem("AskSpec-classic-1")
      try {
        case class Ping(respondTo: ActorRef[AnyRef])
        val ex = new RuntimeException("not good!")

        class LegacyActor extends pekko.actor.Actor {
          def receive = {
            case Ping(respondTo) => respondTo ! pekko.actor.Status.Failure(ex)
          }
        }

        val legacyActor = classicSystem.actorOf(pekko.actor.Props(new LegacyActor))

        import scaladsl.AskPattern._

        import pekko.actor.typed.scaladsl.adapter._
        implicit val timeout: Timeout = 3.seconds
        val typedLegacy: ActorRef[AnyRef] = legacyActor
        typedLegacy.ask(Ping.apply).failed.futureValue should ===(ex)
      } finally {
        pekko.testkit.TestKit.shutdownActorSystem(classicSystem)
      }
    }

    "fail asking actor if responder function throws" in {
      case class Question(reply: ActorRef[Long])

      val probe = TestProbe[AnyRef]("probe")
      val behv =
        Behaviors.receive[String] {
          case (context, "start-ask") =>
            context.ask[Question, Long](probe.ref, Question(_)) {
              case Success(42L) =>
                throw new RuntimeException("Unsupported number")
              case _ => "test"
            }
            Behaviors.same
          case (_, "test") =>
            probe.ref ! "got-test"
            Behaviors.same
          case (_, "get-state") =>
            probe.ref ! "running"
            Behaviors.same
          case (_, _) =>
            Behaviors.unhandled
        }

      val ref = spawn(behv)

      ref ! "test"
      probe.expectMessage("got-test")

      ref ! "start-ask"
      val Question(replyRef) = probe.expectMessageType[Question]
      replyRef ! 0L
      probe.expectMessage("got-test")

      ref ! "start-ask"
      val Question(replyRef2) = probe.expectMessageType[Question]

      LoggingTestKit
        .error("Unsupported number")
        .expect {
          replyRef2 ! 42L
        }(system)

      probe.expectTerminated(ref, probe.remainingOrDefault)
    }
  }

  case class Request(replyTo: ActorRef[StatusReply[String]])

  "askWithStatus pattern" must {
    "unwrap nested response a successful response" in {
      val probe = createTestProbe[Request]()
      val result: Future[String] = probe.ref.askWithStatus(Request(_))
      probe.expectMessageType[Request].replyTo ! StatusReply.success("goodie")
      result.futureValue should ===("goodie")
    }
    "fail future for a fail response with text" in {
      val probe = createTestProbe[Request]()
      val result: Future[String] = probe.ref.askWithStatus(Request(_))
      probe.expectMessageType[Request].replyTo ! StatusReply.error("boom")
      val exception = result.failed.futureValue
      exception should be(a[StatusReply.ErrorMessage])
      exception.getMessage should ===("boom")
    }
    "fail future for a fail response with custom exception" in {
      val probe = createTestProbe[Request]()
      val result: Future[String] = probe.ref.askWithStatus(Request(_))
      probe.expectMessageType[Request].replyTo ! StatusReply.error(TestException("boom"))
      val exception = result.failed.futureValue
      exception should be(a[TestException])
      exception.getMessage should ===("boom")
    }

  }
}
