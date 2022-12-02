/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.pubsub

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.DeadLetter
import pekko.cluster.pubsub.DistributedPubSubMediator.{ Subscribe, _ }
import pekko.testkit._

object DistributedPubSubMediatorDeadLettersSpec {
  def config(sendToDeadLettersWhenNoSubscribers: Boolean) =
    s"""
    pekko.loglevel = INFO
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port=0
    pekko.remote.artery.canonical.port=0
    pekko.remote.log-remote-lifecycle-events = off
    pekko.cluster.pub-sub.send-to-dead-letters-when-no-subscribers = $sendToDeadLettersWhenNoSubscribers
  """
}

trait DeadLettersProbe { this: TestKitBase =>
  val deadLettersProbe = TestProbe()
  system.eventStream.subscribe(deadLettersProbe.ref, classOf[DeadLetter])

  def expectNoDeadLetters(): Unit = deadLettersProbe.expectNoMessage(100.milliseconds)
  def expectDeadLetter(): Unit = deadLettersProbe.expectMsgClass(classOf[DeadLetter])
}

class DistributedPubSubMediatorSendingToDeadLettersSpec
    extends AkkaSpec(DistributedPubSubMediatorDeadLettersSpec.config(sendToDeadLettersWhenNoSubscribers = true))
    with DeadLettersProbe {

  val mediator = DistributedPubSub(system).mediator
  val msg = "hello"
  val testActorPath = testActor.path.toStringWithoutAddress

  "A DistributedPubSubMediator with sending to dead letters enabled" must {
    "send a message to dead letters" when {
      "it is published to a topic with no subscribers" in {
        mediator ! Publish("nowhere", msg)
        expectDeadLetter()
      }

      "it is sent to a logical path with no matching actors" in {
        mediator ! Send("some/random/path", msg, localAffinity = false)
        expectDeadLetter()
      }

      "it is sent to all actors at a logical path with no matching actors" in {
        mediator ! SendToAll("some/random/path", msg)
        expectDeadLetter()
      }
    }

    "not send message to dead letters" when {
      "it is published to a topic with at least one subscriber" in {
        mediator ! Subscribe("somewhere", testActor)
        mediator ! Publish("somewhere", msg)
        expectNoDeadLetters()
      }

      "it is sent to a logical path with at least one matching actor" in {
        mediator ! Put(testActor)
        mediator ! Send(testActorPath, msg, localAffinity = false)
        expectNoDeadLetters()
      }

      "it is sent to all actors at a logical path with at least one matching actor" in {
        mediator ! Put(testActor)
        mediator ! SendToAll(testActorPath, msg)
        expectNoDeadLetters()
      }
    }
  }
}

class DistributedPubSubMediatorNotSendingToDeadLettersSpec
    extends AkkaSpec(DistributedPubSubMediatorDeadLettersSpec.config(sendToDeadLettersWhenNoSubscribers = false))
    with DeadLettersProbe {

  val mediator = DistributedPubSub(system).mediator
  val msg = "hello"
  val testActorPath = testActor.path.toStringWithoutAddress

  "A DistributedPubSubMediator with sending to dead letters disabled" must {
    "not send message to dead letters" when {
      "it is published to a topic with no subscribers" in {
        mediator ! Publish("nowhere", msg)
        expectNoDeadLetters()
      }

      "it is sent to a logical path with no matching actors" in {
        mediator ! Send("some/random/path", msg, localAffinity = false)
        expectNoDeadLetters()
      }

      "it is sent to all actors at a logical path with no matching actors" in {
        mediator ! SendToAll("some/random/path", msg)
        expectNoDeadLetters()
      }

      "it is published to a topic with at least one subscriber" in {
        mediator ! Subscribe("somewhere", testActor)
        mediator ! Publish("somewhere", msg)
        expectNoDeadLetters()
      }

      "it is sent to a logical path with at least one matching actor" in {
        mediator ! Put(testActor)
        mediator ! Send(testActorPath, msg, localAffinity = false)
        expectNoDeadLetters()
      }

      "it is sent to all actors at a logical path with at least one matching actor" in {
        mediator ! Put(testActor)
        mediator ! SendToAll(testActorPath, msg)
        expectNoDeadLetters()
      }
    }
  }
}
