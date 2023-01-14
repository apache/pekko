/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.actor.typed

import org.apache.pekko.Done
import org.apache.pekko.actor.{ Cancellable, CoordinatedShutdown }
import org.apache.pekko.actor.typed.{ ActorRef, ActorSystem, Behavior }
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

class CoordinatedActorShutdownSpec {

  // #coordinated-shutdown-addTask
  object MyActor {

    trait Messages
    case class Stop(replyTo: ActorRef[Done]) extends Messages

    def behavior: Behavior[Messages] =
      Behaviors.receiveMessage {
        // ...
        case Stop(replyTo) =>
          // shut down the actor internals
          // ..
          replyTo.tell(Done)
          Behaviors.stopped
      }
  }

  // #coordinated-shutdown-addTask

  trait Message

  def root: Behavior[Message] = Behaviors.setup[Message] { context =>
    implicit val system = context.system
    val myActor = context.spawn(MyActor.behavior, "my-actor")
    // #coordinated-shutdown-addTask
    CoordinatedShutdown(context.system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "someTaskName") { () =>
      implicit val timeout: Timeout = 5.seconds
      myActor.ask(MyActor.Stop(_))
    }
    // #coordinated-shutdown-addTask

    Behaviors.empty

  }

  def showCancel(): Unit = {
    val system = ActorSystem(root, "main")

    def cleanup(): Unit = {}
    import system.executionContext
    // #coordinated-shutdown-cancellable
    val c: Cancellable =
      CoordinatedShutdown(system).addCancellableTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "cleanup") { () =>
        Future {
          cleanup()
          Done
        }
      }

    // much later...
    c.cancel()
    // #coordinated-shutdown-cancellable

    // #coordinated-shutdown-jvm-hook
    CoordinatedShutdown(system).addJvmShutdownHook {
      println("custom JVM shutdown hook...")
    }
    // #coordinated-shutdown-jvm-hook

    // don't run this
    def dummy(): Unit = {
      // #coordinated-shutdown-run
      // shut down with `ActorSystemTerminateReason`
      system.terminate()

      // or define a specific reason
      case object UserInitiatedShutdown extends CoordinatedShutdown.Reason

      val done: Future[Done] = CoordinatedShutdown(system).run(UserInitiatedShutdown)
      // #coordinated-shutdown-run
    }
  }
}
