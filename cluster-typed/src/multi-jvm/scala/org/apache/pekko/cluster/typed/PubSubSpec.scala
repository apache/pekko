/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.typed

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.actor.typed.pubsub.Topic
import pekko.actor.typed.scaladsl.adapter._
import pekko.cluster.MultiNodeClusterSpec
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.serialization.jackson.CborSerializable

import com.typesafe.config.ConfigFactory

object PubSubSpecConfig extends MultiNodeConfig {
  val first: RoleName = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(ConfigFactory.parseString("""
        pekko.loglevel = INFO
      """).withFallback(MultiNodeClusterSpec.clusterConfig))

  nodeConfig(first)(ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = "dc1"
    """))

  nodeConfig(second, third)(ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = "dc2"
    """))

  case class Message(msg: String) extends CborSerializable
}

class PubSubMultiJvmNode1 extends PubSubSpec
class PubSubMultiJvmNode2 extends PubSubSpec
class PubSubMultiJvmNode3 extends PubSubSpec

abstract class PubSubSpec extends MultiNodeSpec(PubSubSpecConfig) with MultiNodeTypedClusterSpec {

  import PubSubSpecConfig._

  var topic: ActorRef[Topic.Command[Message]] = null
  val topicProbe = TestProbe[Message]()
  var otherTopic: ActorRef[Topic.Command[Message]] = null
  val otherTopicProbe = TestProbe[Message]()

  "A cluster" must {
    "be able to form" in {
      formCluster(first, second, third)
    }

    "start a topic on each node" in {
      topic = spawn(Topic[Message]("animals"), "AnimalsTopic")
      topic ! Topic.Subscribe(topicProbe.ref)
      runOn(second, third) {
        otherTopic = system.actorOf(PropsAdapter(Topic[Message]("other"))).toTyped[Topic.Command[Message]]
        otherTopic ! Topic.Subscribe(otherTopicProbe.ref)
      }
      enterBarrier("topics started")
    }

    "see nodes with subscribers registered" in {
      val statsProbe = TestProbe[Topic.TopicStats]()
      statsProbe.awaitAssert {
        topic ! Topic.GetTopicStats[Message](statsProbe.ref)
        statsProbe.receiveMessage().topicInstanceCount should ===(3)
      }
      enterBarrier("topic instances with subscribers seen")
    }

    "publish to all nodes" in {
      runOn(first) {
        topic ! Topic.Publish(Message("monkey"))
      }
      enterBarrier("first published")
      topicProbe.expectMessage(Message("monkey"))
      runOn(second, third) {
        // check that messages are not leaking between topics
        otherTopicProbe.expectNoMessage()
      }
      enterBarrier("publish seen")
    }

    "not publish to unsubscribed" in {
      runOn(first) {
        topic ! Topic.Unsubscribe(topicProbe.ref)
        // unsubscribe does not need to be gossiped before it is effective
        val statsProbe = TestProbe[Topic.TopicStats]()
        statsProbe.awaitAssert {
          topic ! Topic.GetTopicStats[Message](statsProbe.ref)
          statsProbe.receiveMessage().topicInstanceCount should ===(2)
        }
      }
      enterBarrier("unsubscribed")
      Thread.sleep(200) // but it needs to reach the topic

      runOn(third) {
        topic ! Topic.Publish(Message("donkey"))
      }
      enterBarrier("second published")
      runOn(second, third) {
        topicProbe.expectMessage(Message("donkey"))
      }
      runOn(first) {
        topicProbe.expectNoMessage()
      }
    }

  }
}
