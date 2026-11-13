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

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._
import scala.reflect.ClassTag

import org.apache.pekko
import pekko.actor.Cancellable
import pekko.actor.Dropped
import pekko.actor.InvalidMessageException
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.PostStop
import pekko.actor.typed.Signal
import pekko.actor.typed.pubsub.Topic
import pekko.actor.typed.receptionist.Receptionist
import pekko.actor.typed.receptionist.ServiceKey
import pekko.actor.typed.scaladsl.AbstractBehavior
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.actor.typed.scaladsl.adapter._
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
  final case class StartupReplayWindowElapsed[T]() extends Command[T]

  private final case class Buffered[T](message: T, delivered: Boolean)
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

  /*
   * Startup replay window: the other topic instances are only learnt from the receptionist, which is eventually
   * consistent. The first listing arrives some time after the topic actor started, and with the cluster
   * receptionist it only contains what this node has seen so far. To not lose the first messages published to a
   * new topic instance, messages published until both the first listing has been seen and the configured
   * startup-replay-window has elapsed are kept, and are also delivered to every other topic instance and local
   * subscriber that becomes known during that time. Each of them gets each kept message at most once.
   */
  private val pubSubConfig = context.system.settings.config.getConfig("pekko.actor.typed.pubsub")
  private val startupReplayWindow =
    pubSubConfig.getDuration("startup-replay-window", TimeUnit.MILLISECONDS).millis
  private val startupReplayBufferSize = pubSubConfig.getInt("startup-replay-buffer-size")

  private var replayWindowOpen = true
  private var firstListingSeen = false
  private var replayWindowElapsed = startupReplayWindow <= Duration.Zero
  private var replayBuffer = Vector.empty[Buffered[T]]
  private var replayBufferFullLogged = false
  // other topic instances and local subscribers that already got all messages in the replay buffer
  private var replayedToInstances = Set.empty[ActorRef[TopicImpl.Command[T]]]
  private var replayedToSubscribers = Set.empty[ActorRef[T]]
  private val replayWindowTimer: Option[Cancellable] =
    if (replayWindowElapsed) None
    else Some(context.scheduleOnce(startupReplayWindow, context.self, StartupReplayWindowElapsed[T]()))

  private val receptionist = context.system.receptionist
  private val receptionistAdapter = context.messageAdapter[Receptionist.Listing] {
    case topicServiceKey.Listing(topics) => TopicInstancesUpdated(topics)
    case _                               => throw new IllegalArgumentException() // FIXME exhaustiveness check fails on receptionist listing match
  }
  receptionist ! Receptionist.Subscribe(topicServiceKey, receptionistAdapter)

  override def onMessage(msg: TopicImpl.Command[T]): Behavior[TopicImpl.Command[T]] = msg match {

    case Publish(message) =>
      // local subscribers are always delivered to directly, the own instance is not sent the message
      // even if it is registered, so that local subscribers get it exactly once
      val otherTopicInstances = topicInstances - context.self
      val delivered = localSubscribers.nonEmpty || otherTopicInstances.nonEmpty
      if (delivered) {
        context.log.trace("Publishing message of type [{}]", message.getClass)
        localSubscribers.foreach(_ ! message)
        if (otherTopicInstances.nonEmpty) {
          val pub = MessagePublished(message)
          otherTopicInstances.foreach(_ ! pub)
        }
      }
      if (replayWindowOpen && replayBuffer.size < startupReplayBufferSize) {
        context.log.trace("Keeping message of type [{}] published during startup replay window", message.getClass)
        replayBuffer :+= Buffered(message, delivered)
      } else {
        if (replayWindowOpen && !replayBufferFullLogged) {
          replayBufferFullLogged = true
          context.log.debug(
            "Startup replay buffer of topic [{}] full ([{}] messages), topic instances and subscribers that " +
            "become known later will not get the messages published from now on before they became known",
            topicName,
            startupReplayBufferSize)
        }
        if (!delivered) {
          context.log.trace("Publishing message of type [{}] but no subscribers, dropping", message.getClass)
          dropNoSubscribers(message)
        }
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
        if (replayWindowOpen && !replayedToSubscribers.contains(subscriber)) {
          replayedToSubscribers += subscriber
          replayTo(subscriber, replayBuffer.map(_.message))
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
      if (replayWindowOpen) {
        val newInstances = newTopics - context.self -- replayedToInstances
        if (newInstances.nonEmpty) {
          replayedToInstances ++= newInstances
          val messages = replayBuffer.map(_.message)
          newInstances.foreach(instance => replayTo(instance, messages.map(MessagePublished(_))))
        }
        firstListingSeen = true
        closeReplayWindowIfDone()
      }
      this

    case StartupReplayWindowElapsed() =>
      replayWindowElapsed = true
      closeReplayWindowIfDone()
      this

    case GetTopicStats(replyTo) =>
      replyTo ! TopicStats(localSubscribers.size, topicInstances.size)
      this

    case other =>
      // can't do exhaustiveness check correctly because of protocol internal/public design
      throw new IllegalArgumentException(s"Unexpected command type ${other.getClass}")
  }

  override def onSignal: PartialFunction[Signal, Behavior[TopicImpl.Command[T]]] = {
    case PostStop =>
      replayWindowTimer.foreach(_.cancel())
      this
  }

  private def replayTo[M](recipient: ActorRef[M], messages: Vector[M]): Unit =
    if (messages.nonEmpty) {
      context.log.debug("Replaying [{}] messages published during startup to [{}]", messages.size, recipient)
      messages.foreach(recipient ! _)
      replayBuffer = replayBuffer.map(_.copy(delivered = true))
    }

  private def closeReplayWindowIfDone(): Unit =
    if (replayWindowOpen && firstListingSeen && replayWindowElapsed) {
      context.log.debug("Startup replay window of topic [{}] closed", topicName)
      replayBuffer.foreach { b =>
        if (!b.delivered) dropNoSubscribers(b.message)
      }
      replayWindowOpen = false
      replayBuffer = Vector.empty
      replayedToInstances = Set.empty
      replayedToSubscribers = Set.empty
    }

  private def dropNoSubscribers(message: T): Unit =
    context.system.deadLetters ! Dropped(message, "No topic subscribers known", context.self.toClassic)
}
