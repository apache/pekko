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

package org.apache.pekko.cluster.pubsub

import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.routing.{ ConsistentHashingRoutingLogic, RouterEnvelope }
import pekko.testkit._

case class WrappedMessage(msg: String) extends RouterEnvelope {
  override def message = msg
}

case class UnwrappedMessage(msg: String)

object DistributedPubSubMediatorRouterSpec {
  def config(routingLogic: String) = s"""
    pekko.loglevel = INFO
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port=0
    pekko.remote.artery.canonical.port=0
    pekko.remote.log-remote-lifecycle-events = off
    pekko.cluster.pub-sub.routing-logic = $routingLogic
  """
}

trait DistributedPubSubMediatorRouterSpec { this: AnyWordSpecLike with TestKit with ImplicitSender =>
  def nonUnwrappingPubSub(mediator: ActorRef, testActor: ActorRef, msg: Any): Unit = {

    val path = testActor.path.toStringWithoutAddress

    "keep the RouterEnvelope when sending to a local logical path" in {

      mediator ! DistributedPubSubMediator.Put(testActor)

      mediator ! DistributedPubSubMediator.Send(path, msg, localAffinity = true)
      expectMsg(msg)

      mediator ! DistributedPubSubMediator.Remove(path)
    }

    "keep the RouterEnvelope when sending to a logical path" in {

      mediator ! DistributedPubSubMediator.Put(testActor)

      mediator ! DistributedPubSubMediator.Send(path, msg, localAffinity = false)
      expectMsg(msg)

      mediator ! DistributedPubSubMediator.Remove(path)
    }

    "keep the RouterEnvelope when sending to all actors on a logical path" in {

      mediator ! DistributedPubSubMediator.Put(testActor)

      mediator ! DistributedPubSubMediator.SendToAll(path, msg)
      expectMsg(msg) // SendToAll does not use provided RoutingLogic

      mediator ! DistributedPubSubMediator.Remove(path)
    }

    "keep the RouterEnvelope when sending to a topic" in {

      mediator ! DistributedPubSubMediator.Subscribe("topic", testActor)
      expectMsgClass(classOf[DistributedPubSubMediator.SubscribeAck])

      mediator ! DistributedPubSubMediator.Publish("topic", msg)
      expectMsg(msg) // Publish(... sendOneMessageToEachGroup = false) does not use provided RoutingLogic

      mediator ! DistributedPubSubMediator.Unsubscribe("topic", testActor)
      expectMsgClass(classOf[DistributedPubSubMediator.UnsubscribeAck])
    }

    "keep the RouterEnvelope when sending to a topic for a group" in {

      mediator ! DistributedPubSubMediator.Subscribe("topic", Some("group"), testActor)
      expectMsgClass(classOf[DistributedPubSubMediator.SubscribeAck])

      mediator ! DistributedPubSubMediator.Publish("topic", msg, sendOneMessageToEachGroup = true)
      expectMsg(msg)

      mediator ! DistributedPubSubMediator.Unsubscribe("topic", testActor)
      expectMsgClass(classOf[DistributedPubSubMediator.UnsubscribeAck])
    }
  }
}

class DistributedPubSubMediatorWithRandomRouterSpec
    extends PekkoSpec(DistributedPubSubMediatorRouterSpec.config("random"))
    with DistributedPubSubMediatorRouterSpec
    with DefaultTimeout
    with ImplicitSender {

  val mediator = DistributedPubSub(system).mediator

  "DistributedPubSubMediator when sending wrapped message" must {
    val msg = WrappedMessage("hello")
    behave.like(nonUnwrappingPubSub(mediator, testActor, msg))
  }

  "DistributedPubSubMediator when sending unwrapped message" must {
    val msg = UnwrappedMessage("hello")
    behave.like(nonUnwrappingPubSub(mediator, testActor, msg))
  }
}

class DistributedPubSubMediatorWithHashRouterSpec
    extends PekkoSpec(DistributedPubSubMediatorRouterSpec.config("consistent-hashing"))
    with DistributedPubSubMediatorRouterSpec
    with DefaultTimeout
    with ImplicitSender {

  "DistributedPubSubMediator with Consistent Hash router" must {
    "not be allowed" when {
      "constructed by extension" in {
        intercept[IllegalArgumentException] {
          DistributedPubSub(system).mediator
        }
      }
      "constructed by settings" in {
        intercept[IllegalArgumentException] {
          val config = ConfigFactory
            .parseString(DistributedPubSubMediatorRouterSpec.config("random"))
            .withFallback(system.settings.config)
            .getConfig("pekko.cluster.pub-sub")
          DistributedPubSubSettings(config).withRoutingLogic(ConsistentHashingRoutingLogic(system))
        }
      }
    }
  }
}
