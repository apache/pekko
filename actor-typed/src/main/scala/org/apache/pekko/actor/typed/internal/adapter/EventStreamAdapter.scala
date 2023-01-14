/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal.adapter

import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.eventstream.EventStream
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter._
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 * Encapsulates the [[pekko.actor.ActorSystem.eventStream]] in a [[Behavior]]
 */
@InternalApi private[pekko] object EventStreamAdapter {

  private[pekko] val behavior: Behavior[EventStream.Command] =
    Behaviors.setup { ctx =>
      val eventStream = ctx.system.toClassic.eventStream
      eventStreamBehavior(eventStream)
    }

  private def eventStreamBehavior(eventStream: pekko.event.EventStream): Behavior[EventStream.Command] =
    Behaviors.receiveMessage {
      case EventStream.Publish(event) =>
        eventStream.publish(event)
        Behaviors.same
      case s @ EventStream.Subscribe(subscriber) =>
        eventStream.subscribe(subscriber.toClassic, s.topic)
        Behaviors.same
      case EventStream.Unsubscribe(subscriber) =>
        eventStream.unsubscribe(subscriber.toClassic)
        Behaviors.same
    }

}
