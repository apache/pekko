/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.typed;

// #imports
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
// #imports
import org.apache.pekko.actor.typed.Terminated;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
// #imports
import org.apache.pekko.actor.typed.javadsl.AbstractMatchingBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
// #imports
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;

public interface OnMessageIntroTest {

  // #chatroom-behavior
  public class ChatRoom {
    // #chatroom-behavior
    // #chatroom-protocol
    static interface RoomCommand {}

    public static final class GetSession implements RoomCommand {
      public final String screenName;
      public final ActorRef<SessionEvent> replyTo;

      public GetSession(String screenName, ActorRef<SessionEvent> replyTo) {
        this.screenName = screenName;
        this.replyTo = replyTo;
      }
    }

    // #chatroom-protocol
    private static final class PublishSessionMessage implements RoomCommand {
      public final String screenName;
      public final String message;

      public PublishSessionMessage(String screenName, String message) {
        this.screenName = screenName;
        this.message = message;
      }
    }

    // #chatroom-protocol

    static interface SessionEvent {}

    public static final class SessionGranted implements SessionEvent {
      public final ActorRef<PostMessage> handle;

      public SessionGranted(ActorRef<PostMessage> handle) {
        this.handle = handle;
      }
    }

    public static final class SessionDenied implements SessionEvent {
      public final String reason;

      public SessionDenied(String reason) {
        this.reason = reason;
      }
    }

    public static final class MessagePosted implements SessionEvent {
      public final String screenName;
      public final String message;

      public MessagePosted(String screenName, String message) {
        this.screenName = screenName;
        this.message = message;
      }
    }

    static interface SessionCommand {}

    public static final class PostMessage implements SessionCommand {
      public final String message;

      public PostMessage(String message) {
        this.message = message;
      }
    }

    private static final class NotifyClient implements SessionCommand {
      final MessagePosted message;

      NotifyClient(MessagePosted message) {
        this.message = message;
      }
    }

    // #chatroom-protocol
    // #chatroom-behavior

    public static Behavior<RoomCommand> create() {
      return Behaviors.setup(ChatRoomBehavior::new);
    }

    public static class ChatRoomBehavior extends AbstractMatchingBehavior<RoomCommand> {
      final List<ActorRef<SessionCommand>> sessions = new ArrayList<>();

      private ChatRoomBehavior(ActorContext<RoomCommand> context) {
        super(context);
      }

      @Override
      public Behavior<RoomCommand> onMessage(RoomCommand msg) throws UnsupportedEncodingException {
        // #chatroom-behavior
        /* From Java 16 onward, various features broadly described as "pattern matching"
         * prove useful here in lieu of the explicit instanceof checks and casts:
         *
         * Java 16 onward: JEP 394 (https://openjdk.java.net/jeps/394) =>
         *   if (msg instanceof GetSession gs) {
         *     return onGetSession(gs);
         *   } else if (msg instanceof PublishSessionMessage psm) {
         *     return onPublishSessionMessage(psm);
         *   }
         *
         * Java 21 onward: JEP 441 (https://openjdk.org/jeps/441 =>
         // #chatroom-behavior
        // uses Java 21-onward features
        switch(msg) {
          case GetSession gs:
            return onGetSession(gs);

          case PublishSessionMessage psm:
            return onPublishSessionMessage(psm);

        }
        // #chatroom-behavior
         *
         */
        if (msg instanceof GetSession) {
          return onGetSession((GetSession) msg);
        } else if (msg instanceof PublishSessionMessage) {
          return onPublishSessionMessage((PublishSessionMessage) msg);
        }

        // for completeness
        // #chatroom-behavior
        return Behaviors.unhandled();
      }

      private Behavior<RoomCommand> onGetSession(GetSession gs)
          throws UnsupportedEncodingException {
        ActorRef<SessionEvent> client = gs.replyTo;
        ActorRef<SessionCommand> ses =
            getContext()
                .spawn(
                    SessionBehavior.create(getContext().getSelf(), gs.screenName, client),
                    URLEncoder.encode(gs.screenName, StandardCharsets.UTF_8.name()));

        // narrow to only expose PostMessage
        client.tell(new SessionGranted(ses.narrow()));
        sessions.add(ses);

        return this;
      }

      private Behavior<RoomCommand> onPublishSessionMessage(PublishSessionMessage pub) {
        NotifyClient notification =
            new NotifyClient(new MessagePosted(pub.screenName, pub.message));

        sessions.forEach(s -> s.tell(notification));
        return this;
      }
    }

    static class SessionBehavior extends AbstractMatchingBehavior<SessionCommand> {
      private final ActorRef<RoomCommand> room;
      private final String screenName;
      private final ActorRef<SessionEvent> client;

      public static Behavior<SessionCommand> create(
          ActorRef<RoomCommand> room, String screenName, ActorRef<SessionEvent> client) {
        return Behaviors.setup(context -> new SessionBehavior(context, room, screenName, client));
      }

      private SessionBehavior(
          ActorContext<SessionCommand> context,
          ActorRef<RoomCommand> room,
          String screenName,
          ActorRef<SessionEvent> client) {
        super(context);
        this.room = room;
        this.screenName = screenName;
        this.client = client;
      }

      @Override
      public Behavior<SessionCommand> onMessage(SessionCommand msg) {
        // #chatroom-behavior
        if (msg instanceof PostMessage) {
          // from client, publish to others via the room
          room.tell(new PublishSessionMessage(screenName, ((PostMessage) msg).message));
          return Behaviors.same();
        } else if (msg instanceof NotifyClient) {
          // published from the room
          client.tell(((NotifyClient) msg).message);
          return Behaviors.same();
        }

        // for completeness
        /*
        // #chatroom-behavior
        // Java 21 onward: JEP 441 (https://openjdk.org/jeps/441) =>
        switch (msg) {
          case PostMessage pm:
            // from client, publish to others via the room
            room.tell(new PublishSessionMessage(screenName, pm.message);
            return Behaviors.same();

          case NotifyClient nc:
            // published from the room
            client.tell(nc.message);
            return Behaviors.same();

        }
        // #chatroom-behavior
        */
        // #chatroom-behavior

        return Behaviors.unhandled();
      }
    }
  }

  // #chatroom-behavior

  // NB: leaving the gabbler as an AbstractBehavior, as the point should be made by now
  // #chatroom-gabbler
  public class Gabbler extends AbstractBehavior<ChatRoom.SessionEvent> {
    public static Behavior<ChatRoom.SessionEvent> create() {
      return Behaviors.setup(Gabbler::new);
    }

    private Gabbler(ActorContext<ChatRoom.SessionEvent> context) {
      super(context);
    }

    @Override
    public Receive<ChatRoom.SessionEvent> createReceive() {
      ReceiveBuilder<ChatRoom.SessionEvent> builder = newReceiveBuilder();
      return builder
          .onMessage(ChatRoom.SessionDenied.class, this::onSessionDenied)
          .onMessage(ChatRoom.SessionGranted.class, this::onSessionGranted)
          .onMessage(ChatRoom.MessagePosted.class, this::onMessagePosted)
          .build();
    }

    private Behavior<ChatRoom.SessionEvent> onSessionDenied(ChatRoom.SessionDenied message) {
      getContext().getLog().info("cannot start chat room session: {}", message.reason);
      return Behaviors.stopped();
    }

    private Behavior<ChatRoom.SessionEvent> onSessionGranted(ChatRoom.SessionGranted message) {
      message.handle.tell(new ChatRoom.PostMessage("Hello World!"));
      return Behaviors.same();
    }

    private Behavior<ChatRoom.SessionEvent> onMessagePosted(ChatRoom.MessagePosted message) {
      getContext()
          .getLog()
          .info("message has been posted by '{}': {}", message.screenName, message.message);
      return Behaviors.stopped();
    }
  }

  // #chatroom-gabbler

  // #chatroom-main
  public class Main {
    public static Behavior<Void> create() {
      return Behaviors.setup(
          context -> {
            ActorRef<ChatRoom.RoomCommand> chatRoom = context.spawn(ChatRoom.create(), "chatRoom");
            ActorRef<ChatRoom.SessionEvent> gabbler = context.spawn(Gabbler.create(), "gabbler");
            context.watch(gabbler);
            chatRoom.tell(new ChatRoom.GetSession("ol’ Gabbler", gabbler));

            return Behaviors.receive(Void.class)
                .onSignal(Terminated.class, sig -> Behaviors.stopped())
                .build();
          });
    }

    public static void main(String[] args) {
      ActorSystem.create(Main.create(), "ChatRoomDemo");
    }
  }
  // #chatroom-main
}
