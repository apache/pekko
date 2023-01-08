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

package docs.org.apache.pekko.typed

import scala.concurrent.duration._
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import scala.annotation.nowarn
import org.scalatest.wordspec.AnyWordSpecLike

object FaultToleranceDocSpec {
  // #bubbling-example
  import org.apache.pekko
  import pekko.actor.typed.ActorRef
  import pekko.actor.typed.Behavior
  import pekko.actor.typed.DeathPactException
  import pekko.actor.typed.SupervisorStrategy
  import pekko.actor.typed.scaladsl.Behaviors

  object Protocol {
    sealed trait Command
    case class Fail(text: String) extends Command
    case class Hello(text: String, replyTo: ActorRef[String]) extends Command
  }
  import Protocol._

  object Worker {
    def apply(): Behavior[Command] =
      Behaviors.receiveMessage {
        case Fail(text) =>
          throw new RuntimeException(text)
        case Hello(text, replyTo) =>
          replyTo ! text
          Behaviors.same
      }
  }

  object MiddleManagement {
    def apply(): Behavior[Command] =
      Behaviors.setup[Command] { context =>
        context.log.info("Middle management starting up")
        // default supervision of child, meaning that it will stop on failure
        val child = context.spawn(Worker(), "child")
        // we want to know when the child terminates, but since we do not handle
        // the Terminated signal, we will in turn fail on child termination
        context.watch(child)

        // here we don't handle Terminated at all which means that
        // when the child fails or stops gracefully this actor will
        // fail with a DeathPactException
        Behaviors.receiveMessage { message =>
          child ! message
          Behaviors.same
        }
      }
  }

  object Boss {
    def apply(): Behavior[Command] =
      Behaviors
        .supervise(Behaviors.setup[Command] { context =>
          context.log.info("Boss starting up")
          // default supervision of child, meaning that it will stop on failure
          val middleManagement = context.spawn(MiddleManagement(), "middle-management")
          context.watch(middleManagement)

          // here we don't handle Terminated at all which means that
          // when middle management fails with a DeathPactException
          // this actor will also fail
          Behaviors.receiveMessage[Command] { message =>
            middleManagement ! message
            Behaviors.same
          }
        })
        .onFailure[DeathPactException](SupervisorStrategy.restart)
  }
  // #bubbling-example
}

@nowarn("msg=never used")
class FaultToleranceDocSpec extends ScalaTestWithActorTestKit("""
      # silenced to not put noise in test logs
      pekko.loglevel = off
    """) with AnyWordSpecLike {
  import FaultToleranceDocSpec._

  "Bubbling of failures" must {

    "have an example for the docs" in {
      val boss = spawn(Boss(), "upper-management")
      val replyProbe = createTestProbe[String]()
      boss ! Protocol.Hello("hi 1", replyProbe.ref)
      replyProbe.expectMessage("hi 1")
      boss ! Protocol.Fail("ping")

      // message may be lost when MiddleManagement is stopped, but eventually it will be functional again
      eventually {
        boss ! Protocol.Hello("hi 2", replyProbe.ref)
        replyProbe.expectMessage(200.millis, "hi 2")
      }

    }
  }

}
