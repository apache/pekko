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

package jdocs.org.apache.pekko.typed.pubsub;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

// #start-topic
import org.apache.pekko.actor.typed.pubsub.Topic;

// #start-topic

public class PubSubExample {

  static class Message {
    public final String text;

    public Message(String text) {
      this.text = text;
    }
  }

  private Behavior<?> behavior =
      // #start-topic
      Behaviors.setup(
          context -> {
            ActorRef<Topic.Command<Message>> topic =
                context.spawn(Topic.create(Message.class, "my-topic"), "MyTopic");
            // #start-topic

            ActorRef<Message> subscriberActor = null;
            // #subscribe
            topic.tell(Topic.subscribe(subscriberActor));

            topic.tell(Topic.unsubscribe(subscriberActor));
            // #subscribe

            // #publish
            topic.tell(Topic.publish(new Message("Hello Subscribers!")));
            // #publish

            return Behaviors.empty();
          });
}
