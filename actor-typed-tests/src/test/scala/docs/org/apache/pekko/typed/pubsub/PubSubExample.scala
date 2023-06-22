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

package docs.org.apache.pekko.typed.pubsub

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors

object PubSubExample {

  case class Message(text: String)

  def example: Behavior[Any] = {
    // #start-topic
    import org.apache.pekko.actor.typed.pubsub.Topic

    Behaviors.setup { context =>
      val topic = context.spawn(Topic[Message]("my-topic"), "MyTopic")
      // #start-topic

      val subscriberActor: ActorRef[Message] = ???
      // #subscribe
      topic ! Topic.Subscribe(subscriberActor)

      topic ! Topic.Unsubscribe(subscriberActor)
      // #subscribe

      // #publish
      topic ! Topic.Publish(Message("Hello Subscribers!"))
      // #publish

      Behaviors.empty
    }
  }

}
