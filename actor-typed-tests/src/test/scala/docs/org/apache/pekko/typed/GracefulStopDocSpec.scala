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

//#imports
import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.{ ActorSystem, PostStop }

//#imports

import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.typed.ActorRef
import scala.concurrent.duration._
import scala.concurrent.Await
import org.scalatest.wordspec.AnyWordSpecLike
import pekko.actor.typed.Terminated

import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit

object GracefulStopDocSpec {

  // #master-actor

  object MasterControlProgram {
    sealed trait Command
    final case class SpawnJob(name: String) extends Command
    case object GracefulShutdown extends Command

    def apply(): Behavior[Command] =
      Behaviors
        .receive[Command] { (context, message) =>
          message match {
            case SpawnJob(jobName) =>
              context.log.info("Spawning job {}!", jobName)
              context.spawn(Job(jobName), name = jobName)
              Behaviors.same
            case GracefulShutdown =>
              context.log.info("Initiating graceful shutdown...")
              // Here it can perform graceful stop (possibly asynchronous) and when completed
              // return `Behaviors.stopped` here or after receiving another message.
              Behaviors.stopped
          }
        }
        .receiveSignal {
          case (context, PostStop) =>
            context.log.info("Master Control Program stopped")
            Behaviors.same
        }
  }
  // #master-actor

  // #worker-actor

  object Job {
    sealed trait Command

    def apply(name: String): Behavior[Command] =
      Behaviors.receiveSignal[Command] {
        case (context, PostStop) =>
          context.log.info("Worker {} stopped", name)
          Behaviors.same
      }
  }
  // #worker-actor

  object IllustrateWatch {
    // #master-actor-watch

    object MasterControlProgram {
      sealed trait Command
      final case class SpawnJob(name: String) extends Command

      def apply(): Behavior[Command] =
        Behaviors
          .receive[Command] { (context, message) =>
            message match {
              case SpawnJob(jobName) =>
                context.log.info("Spawning job {}!", jobName)
                val job = context.spawn(Job(jobName), name = jobName)
                context.watch(job)
                Behaviors.same
            }
          }
          .receiveSignal {
            case (context, Terminated(ref)) =>
              context.log.info("Job stopped: {}", ref.path.name)
              Behaviors.same
          }
    }
    // #master-actor-watch
  }

  object IllustrateWatchWith {
    // #master-actor-watchWith

    object MasterControlProgram {
      sealed trait Command
      final case class SpawnJob(name: String, replyToWhenDone: ActorRef[JobDone]) extends Command
      final case class JobDone(name: String)
      private final case class JobTerminated(name: String, replyToWhenDone: ActorRef[JobDone]) extends Command

      def apply(): Behavior[Command] =
        Behaviors.receive { (context, message) =>
          message match {
            case SpawnJob(jobName, replyToWhenDone) =>
              context.log.info("Spawning job {}!", jobName)
              val job = context.spawn(Job(jobName), name = jobName)
              context.watchWith(job, JobTerminated(jobName, replyToWhenDone))
              Behaviors.same
            case JobTerminated(jobName, replyToWhenDone) =>
              context.log.info("Job stopped: {}", jobName)
              replyToWhenDone ! JobDone(jobName)
              Behaviors.same
          }
        }
    }
    // #master-actor-watchWith
  }

}

class GracefulStopDocSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  import GracefulStopDocSpec._

  "Graceful stop example" must {

    "start some workers" in {
      // #start-workers
      import MasterControlProgram._

      val system: ActorSystem[Command] = ActorSystem(MasterControlProgram(), "B6700")

      system ! SpawnJob("a")
      system ! SpawnJob("b")

      // sleep here to allow time for the new actors to be started
      Thread.sleep(100)

      // brutally stop the system
      system.terminate()

      Await.result(system.whenTerminated, 3.seconds)
      // #start-workers
    }

    "gracefully stop workers and master" in {
      import MasterControlProgram._

      val system: ActorSystem[Command] = ActorSystem(MasterControlProgram(), "B7700")

      system ! SpawnJob("a")
      system ! SpawnJob("b")

      Thread.sleep(100)

      // gracefully stop the system
      system ! GracefulShutdown

      Thread.sleep(100)

      Await.result(system.whenTerminated, 3.seconds)
    }
  }
}
