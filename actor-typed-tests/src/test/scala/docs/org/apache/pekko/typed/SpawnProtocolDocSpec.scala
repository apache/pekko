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

package docs.org.apache.pekko.typed

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.ActorTestKit
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import docs.org.apache.pekko.typed.IntroSpec.HelloWorld
import org.scalatest.wordspec.AnyWordSpecLike
import scala.annotation.nowarn

//#imports1
import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.SpawnProtocol
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.LoggerOps

//#imports1

//#imports2
import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Props
import pekko.util.Timeout

//#imports2

object SpawnProtocolDocSpec {

  // Silent because we want to name the unused 'context' parameter
  @nowarn("msg=never used")
  // #main
  object HelloWorldMain {
    def apply(): Behavior[SpawnProtocol.Command] =
      Behaviors.setup { context =>
        // Start initial tasks
        // context.spawn(...)

        SpawnProtocol()
      }
  }
  // #main
}

class SpawnProtocolDocSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  import SpawnProtocolDocSpec._

  "ActorSystem with SpawnProtocol" must {
    "be able to spawn actors" in {
      // #system-spawn

      implicit val system: ActorSystem[SpawnProtocol.Command] =
        ActorSystem(HelloWorldMain(), "hello")

      // needed in implicit scope for ask (?)
      import pekko.actor.typed.scaladsl.AskPattern._
      implicit val ec: ExecutionContext = system.executionContext
      implicit val timeout: Timeout = Timeout(3.seconds)

      val greeter: Future[ActorRef[HelloWorld.Greet]] =
        system.ask(SpawnProtocol.Spawn(behavior = HelloWorld(), name = "greeter", props = Props.empty, _))

      val greetedBehavior = Behaviors.receive[HelloWorld.Greeted] { (context, message) =>
        context.log.info2("Greeting for {} from {}", message.whom, message.from)
        Behaviors.stopped
      }

      val greetedReplyTo: Future[ActorRef[HelloWorld.Greeted]] =
        system.ask(SpawnProtocol.Spawn(greetedBehavior, name = "", props = Props.empty, _))

      for (greeterRef <- greeter; replyToRef <- greetedReplyTo) {
        greeterRef ! HelloWorld.Greet("Pekko", replyToRef)
      }

      // #system-spawn

      Thread.sleep(500) // it will not fail if too short
      ActorTestKit.shutdown(system)
    }

  }

}
