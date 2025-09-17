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

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

//#imports
import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, LoggerOps }
import pekko.actor.typed.{ ActorRef, ActorSystem, Behavior }
//#imports
import org.scalatest.wordspec.AnyWordSpecLike

import pekko.NotUsed
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.Terminated

object OOIntroSpec {

  // #chatroom-protocol
  // #chatroom-behavior
  object ChatRoom {
    // #chatroom-behavior
    sealed trait RoomCommand
    final case class GetSession(screenName: String, replyTo: ActorRef[SessionEvent]) extends RoomCommand
    // #chatroom-protocol
    // #chatroom-behavior
    private final case class PublishSessionMessage(screenName: String, message: String) extends RoomCommand
    // #chatroom-behavior
    // #chatroom-protocol

    sealed trait SessionEvent
    final case class SessionGranted(handle: ActorRef[PostMessage]) extends SessionEvent
    final case class SessionDenied(reason: String) extends SessionEvent
    final case class MessagePosted(screenName: String, message: String) extends SessionEvent

    sealed trait SessionCommand
    final case class PostMessage(message: String) extends SessionCommand
    private final case class NotifyClient(message: MessagePosted) extends SessionCommand
    // #chatroom-protocol
    // #chatroom-behavior

    def apply(): Behavior[RoomCommand] =
      Behaviors.setup(context => new ChatRoomBehavior(context))

    class ChatRoomBehavior(context: ActorContext[RoomCommand]) extends AbstractBehavior[RoomCommand](context) {
      private var sessions: List[ActorRef[SessionCommand]] = List.empty

      override def onMessage(message: RoomCommand): Behavior[RoomCommand] = {
        message match {
          case GetSession(screenName, client) =>
            // create a child actor for further interaction with the client
            val ses = context.spawn(
              SessionBehavior(context.self, screenName, client),
              name = URLEncoder.encode(screenName, StandardCharsets.UTF_8.name))
            client ! SessionGranted(ses)
            sessions = ses :: sessions
            this
          case PublishSessionMessage(screenName, message) =>
            val notification = NotifyClient(MessagePosted(screenName, message))
            sessions.foreach(_ ! notification)
            this
        }
      }
    }

    private object SessionBehavior {
      def apply(
          room: ActorRef[PublishSessionMessage],
          screenName: String,
          client: ActorRef[SessionEvent]): Behavior[SessionCommand] =
        Behaviors.setup(ctx => new SessionBehavior(ctx, room, screenName, client))
    }

    private class SessionBehavior(
        context: ActorContext[SessionCommand],
        room: ActorRef[PublishSessionMessage],
        screenName: String,
        client: ActorRef[SessionEvent])
        extends AbstractBehavior[SessionCommand](context) {

      override def onMessage(msg: SessionCommand): Behavior[SessionCommand] =
        msg match {
          case PostMessage(message) =>
            // from client, publish to others via the room
            room ! PublishSessionMessage(screenName, message)
            Behaviors.same
          case NotifyClient(message) =>
            // published from the room
            client ! message
            Behaviors.same
        }
    }
    // #chatroom-protocol
  }
  // #chatroom-protocol
  // #chatroom-behavior

  // #chatroom-gabbler
  object Gabbler {
    import ChatRoom._

    def apply(): Behavior[SessionEvent] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          case SessionDenied(reason) =>
            context.log.info("cannot start chat room session: {}", reason)
            Behaviors.stopped
          case SessionGranted(handle) =>
            handle ! PostMessage("Hello World!")
            Behaviors.same
          case MessagePosted(screenName, message) =>
            context.log.info2("message has been posted by '{}': {}", screenName, message)
            Behaviors.stopped
        }
      }
    // #chatroom-gabbler
  }

  // #chatroom-main
  object Main {
    def apply(): Behavior[NotUsed] =
      Behaviors.setup { context =>
        val chatRoom = context.spawn(ChatRoom(), "chatroom")
        val gabblerRef = context.spawn(Gabbler(), "gabbler")
        context.watch(gabblerRef)
        chatRoom ! ChatRoom.GetSession("ol’ Gabbler", gabblerRef)

        Behaviors.receiveSignal {
          case (_, Terminated(_)) =>
            Behaviors.stopped
        }
      }

    def main(args: Array[String]): Unit = {
      ActorSystem(Main(), "ChatRoomDemo")
    }

  }
  // #chatroom-main

}

class OOIntroSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  import OOIntroSpec._

  "Intro sample" must {
    "chat" in {
      val system = ActorSystem(Main(), "ChatRoomDemo")
      system.whenTerminated.futureValue should ===(Done)
    }
  }
}
