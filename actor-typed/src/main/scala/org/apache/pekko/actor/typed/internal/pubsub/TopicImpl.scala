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

package org.apache.pekko.actor.typed.internal.pubsub

import scala.reflect.ClassTag

import org.apache.pekko
import pekko.actor.typed.pubsub.Topic
import pekko.actor.typed.receptionist.{ Receptionist, ServiceKey }
import pekko.actor.typed.scaladsl.adapter._
import pekko.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, LoggerOps }
import pekko.actor.typed.{ ActorRef, Behavior }
import pekko.actor.{ Dropped, InvalidMessageException }
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object TopicImpl {

  trait Command[T]

  // actual public messages but internal to ease bincomp evolution
  final case class Publish[T](message: T) extends Topic.Command[T] {
    if (message == null)
      throw InvalidMessageException("[null] is not an allowed message")
  }
  final case class Subscribe[T](subscriber: ActorRef[T]) extends Topic.Command[T]
  final case class Unsubscribe[T](subscriber: ActorRef[T]) extends Topic.Command[T]

  // internal messages, note that the protobuf serializer for those sent remotely is defined in pekko-cluster-typed
  final case class GetTopicStats[T](replyTo: ActorRef[TopicStats]) extends Topic.Command[T]
  final case class TopicStats(localSubscriberCount: Int, topicInstanceCount: Int) extends Topic.TopicStats
  final case class TopicInstancesUpdated[T](topics: Set[ActorRef[TopicImpl.Command[T]]]) extends Command[T]
  final case class MessagePublished[T](message: T) extends Command[T]
  final case class SubscriberTerminated[T](subscriber: ActorRef[T]) extends Command[T]
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class TopicImpl[T](topicName: String, context: ActorContext[TopicImpl.Command[T]])(
    implicit classTag: ClassTag[T])
    extends AbstractBehavior[TopicImpl.Command[T]](context) {

  /*
   * The topic actor keeps a local set of subscribers, whenever that is non-empty it registers itself for
   * a topic service key, and when it becomes empty again it deregisters from the service key. Published
   * messages go to all currently known topics registered for the topic service key and the individual topic
   * instances then broadcast the message to all local subscribers. This achieves deduplication for nodes
   * with multiple subscribers and avoids sending to nodes without any subscribers.
   */

  import TopicImpl._

  private val topicServiceKey = ServiceKey[TopicImpl.Command[T]](topicName)
  context.log.debugN(
    "Starting up pub-sub topic [{}] for messages of type [{}]",
    topicName,
    classTag.runtimeClass.getName)

  private var topicInstances = Set.empty[ActorRef[TopicImpl.Command[T]]]
  private var localSubscribers = Set.empty[ActorRef[T]]

  private val receptionist = context.system.receptionist
  private val receptionistAdapter = context.messageAdapter[Receptionist.Listing] {
    case topicServiceKey.Listing(topics) => TopicInstancesUpdated(topics)
    case _                               => throw new IllegalArgumentException() // FIXME exhaustiveness check fails on receptionist listing match
  }
  receptionist ! Receptionist.Subscribe(topicServiceKey, receptionistAdapter)

  override def onMessage(msg: TopicImpl.Command[T]): Behavior[TopicImpl.Command[T]] = msg match {

    case Publish(message) =>
      if (topicInstances.isEmpty) {
        if (localSubscribers.isEmpty) {
          context.log.trace("Publishing message of type [{}] but no subscribers, dropping", msg.getClass)
          context.system.deadLetters ! Dropped(message, "No topic subscribers known", context.self.toClassic)
        } else {
          context.log.trace(
            "Publishing message of type [{}] to local subscribers only (topic listing not seen yet)",
            msg.getClass)
          localSubscribers.foreach(_ ! message)
        }
      } else {
        context.log.trace("Publishing message of type [{}]", msg.getClass)
        val pub = MessagePublished(message)
        topicInstances.foreach(_ ! pub)
      }
      this

    case MessagePublished(msg) =>
      context.log.trace("Message of type [{}] published", msg.getClass)
      localSubscribers.foreach(_ ! msg)
      this

    case Subscribe(subscriber) =>
      if (!localSubscribers.contains(subscriber)) {
        context.watchWith(subscriber, SubscriberTerminated(subscriber))
        localSubscribers = localSubscribers + subscriber
        if (localSubscribers.size == 1) {
          context.log.debug(
            "Local subscriber [{}] added, went from no subscribers to one, subscribing to receptionist",
            subscriber)
          // we went from no subscribers to one, register to the receptionist
          receptionist ! Receptionist.Register(topicServiceKey, context.self)
        } else {
          context.log.debug("Local subscriber [{}] added", subscriber)
        }
      } else {
        context.log.debug("Local subscriber [{}] already subscribed, ignoring Subscribe command")
      }
      this

    case Unsubscribe(subscriber) =>
      context.unwatch(subscriber)
      localSubscribers = localSubscribers.filterNot(_ == subscriber)
      if (localSubscribers.isEmpty) {
        context.log.debug("Last local subscriber [{}] unsubscribed, deregistering from receptionist", subscriber)
        // that was the lost subscriber, deregister from the receptionist
        receptionist ! Receptionist.Deregister(topicServiceKey, context.self)
      } else {
        context.log.debug("Local subscriber [{}] unsubscribed", subscriber)
      }
      this

    case SubscriberTerminated(subscriber) =>
      localSubscribers -= subscriber
      if (localSubscribers.isEmpty) {
        context.log.debug("Last local subscriber [{}] terminated, deregistering from receptionist", subscriber)
        // that was the last subscriber, deregister from the receptionist
        receptionist ! Receptionist.Deregister(topicServiceKey, context.self)
      } else {
        context.log.debug("Local subscriber [{}] terminated, removing from subscriber list", subscriber)
      }
      this

    case TopicInstancesUpdated(newTopics) =>
      context.log.debug("Topic list updated [{}]", newTopics)
      topicInstances = newTopics
      this

    case GetTopicStats(replyTo) =>
      replyTo ! TopicStats(localSubscribers.size, topicInstances.size)
      this

    case other =>
      // can't do exhaustiveness check correctly because of protocol internal/public design
      throw new IllegalArgumentException(s"Unexpected command type ${other.getClass}")
  }
}
