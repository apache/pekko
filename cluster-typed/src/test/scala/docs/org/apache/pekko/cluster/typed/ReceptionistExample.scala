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

package docs.org.apache.pekko.cluster.typed

//#import
import org.apache.pekko
import pekko.actor.typed.receptionist.{ Receptionist, ServiceKey }
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.{ ActorRef, Behavior }
//#import

object PingPongExample {
  // #ping-service
  object PingService {
    val PingServiceKey = ServiceKey[Ping]("pingService")

    final case class Ping(replyTo: ActorRef[Pong.type])
    case object Pong

    def apply(): Behavior[Ping] = {
      Behaviors.setup { context =>
        context.system.receptionist ! Receptionist.Register(PingServiceKey, context.self)

        Behaviors.receiveMessage {
          case Ping(replyTo) =>
            context.log.info("Pinged by {}", replyTo)
            replyTo ! Pong
            Behaviors.same
        }
      }
    }
  }
  // #ping-service

  // #pinger
  object Pinger {
    def apply(pingService: ActorRef[PingService.Ping]): Behavior[PingService.Pong.type] = {
      Behaviors.setup { context =>
        pingService ! PingService.Ping(context.self)

        Behaviors.receiveMessage { _ =>
          context.log.info("{} was ponged!!", context.self)
          Behaviors.stopped
        }
      }
    }
  }
  // #pinger

  // #pinger-guardian
  object Guardian {
    def apply(): Behavior[Nothing] = {
      Behaviors
        .setup[Receptionist.Listing] { context =>
          context.spawnAnonymous(PingService())
          context.system.receptionist ! Receptionist.Subscribe(PingService.PingServiceKey, context.self)

          Behaviors.receiveMessagePartial[Receptionist.Listing] {
            case PingService.PingServiceKey.Listing(listings) =>
              listings.foreach(ps => context.spawnAnonymous(Pinger(ps)))
              Behaviors.same
          }
        }
        .narrow
    }
  }
  // #pinger-guardian

  // #find
  object PingManager {
    sealed trait Command
    case object PingAll extends Command
    private case class ListingResponse(listing: Receptionist.Listing) extends Command

    def apply(): Behavior[Command] = {
      Behaviors.setup[Command] { context =>
        val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse.apply)

        context.spawnAnonymous(PingService())

        Behaviors.receiveMessagePartial {
          case PingAll =>
            context.system.receptionist ! Receptionist.Find(PingService.PingServiceKey, listingResponseAdapter)
            Behaviors.same
          case ListingResponse(PingService.PingServiceKey.Listing(listings)) =>
            listings.foreach(ps => context.spawnAnonymous(Pinger(ps)))
            Behaviors.same
        }
      }
    }
  }
  // #find

  Behaviors.setup[PingService.Ping] { context =>
    // #deregister
    context.system.receptionist ! Receptionist.Deregister(PingService.PingServiceKey, context.self)
    // #deregister
    Behaviors.empty
  }
}

object ReceptionistExample {
  import pekko.actor.typed.ActorSystem

  import PingPongExample._

  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Nothing](Guardian(), "PingPongExample")
    Thread.sleep(10000)
    system.terminate()
  }

}
