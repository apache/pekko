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

package org.apache.pekko.actor.typed.pubsub

import scala.reflect.ClassTag

import org.apache.pekko
import pekko.actor.typed.internal.pubsub.TopicImpl
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.{ ActorRef, Behavior }
import pekko.annotation.DoNotInherit

/**
 * A pub sub topic is an actor that handles subscribing to a topic and publishing messages to all subscribed actors.
 *
 * It is mostly useful in a clustered setting, where it is intended to be started once on every node that want to
 * house subscribers or publish messages to the topic, but it also works in a local setting without cluster.
 *
 * In a clustered context messages are deduplicated so that there is at most one message sent to each node for
 * each publish and if there are no subscribers on a node, no message is sent to it. Note that the list of subscribers
 * is eventually consistent and there are no delivery guarantees built in.
 *
 * Each topic results in a [[pekko.actor.typed.receptionist.ServiceKey]] in the [[pekko.actor.typed.receptionist.Receptionist]]
 * so the same scaling recommendation holds for topics, see docs:
 * https://pekko.apache.org/docs/pekko/current/typed/actor-discovery.html#receptionist-scalability
 */
object Topic {

  /**
   * Not for user extension
   */
  @DoNotInherit
  trait Command[T] extends TopicImpl.Command[T]

  /**
   * Scala API: Publish the message to all currently known subscribers.
   */
  object Publish {
    def apply[T](message: T): Command[T] =
      TopicImpl.Publish(message)
  }

  /**
   * Java API: Publish the message to all currently known subscribers.
   */
  def publish[T](message: T): Command[T] = Publish(message)

  /**
   * Scala API: Subscribe to this topic. Should only be used for local subscribers.
   */
  object Subscribe {
    def apply[T](subscriber: ActorRef[T]): Command[T] = TopicImpl.Subscribe(subscriber)
  }

  /**
   * Java API: Subscribe to this topic. Should only be used for local subscribers.
   */
  def subscribe[T](subscriber: ActorRef[T]): Command[T] = Subscribe(subscriber)

  /**
   * Scala API: Unsubscribe a previously subscribed actor from this topic.
   */
  object Unsubscribe {
    def apply[T](subscriber: ActorRef[T]): Command[T] = TopicImpl.Unsubscribe(subscriber)
  }

  /**
   * Response to the `GetTopicStats` query.
   *
   * Note that this is a snapshot of the state at one point in time, that there was subscribers at that
   * time does not guarantee there is once this response arrives. The information cannot be used to
   * achieve delivery guarantees, but can be useful in for example tests, to observe a subscription
   * completed before publishing messages.
   *
   * Not for user extension.
   */
  @DoNotInherit
  trait TopicStats {

    /**
     * @return The number of local subscribers subscribing to this topic actor instance when the request was handled
     */
    def localSubscriberCount: Int

    /**
     * @return The number of known other topic actor instances for the topic (locally and across the cluster),
     *         that has at least one subscriber. A topic only be counted towards this sum once it has at least
     *         one subscriber and when the last local subscriber unsubscribes it will be subtracted from this sum
     *         (the value is eventually consistent).
     */
    def topicInstanceCount: Int
  }

  /**
   * Scala API: Get a summary of the state for a local topic actor.
   *
   * See [[TopicStats]] for caveats
   */
  object GetTopicStats {
    def apply[T](replyTo: ActorRef[TopicStats]): Command[T] = TopicImpl.GetTopicStats(replyTo)
  }

  /**
   * Java API: Get a summary of the state for a local topic actor.
   *
   * See [[TopicStats]] for caveats
   */
  def getTopicStats[T](replyTo: ActorRef[TopicStats]): Command[T] =
    GetTopicStats(replyTo)

  /**
   * Java API: Unsubscribe a previously subscribed actor from this topic.
   */
  def unsubscribe[T](subscriber: ActorRef[T]): Command[T] = Unsubscribe(subscriber)

  /**
   * Scala API: Create a topic actor behavior for the given topic name and message type.
   */
  def apply[T](topicName: String)(implicit classTag: ClassTag[T]): Behavior[Command[T]] =
    Behaviors.setup[TopicImpl.Command[T]](context => new TopicImpl[T](topicName, context)).narrow

  /**
   * Java API: Create a topic actor behavior for the given topic name and message class
   */
  def create[T](messageClass: Class[T], topicName: String): Behavior[Command[T]] =
    apply[T](topicName)(ClassTag(messageClass))

}
