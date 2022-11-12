/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.typed.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.internal.pubsub.TopicImpl
import pekko.actor.typed.pubsub.Topic
import pekko.stream.OverflowStrategy
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.scaladsl.TestSink
import org.scalatest.wordspec.AnyWordSpecLike

class PubSubSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "PubSub.source" should {

    "emit messages from the topic" in {
      val topic = testKit.spawn(Topic[String]("my-topic-1"))

      val source = PubSub.source(topic, 100, OverflowStrategy.fail)
      val sourceProbe = source.runWith(TestSink())
      sourceProbe.ensureSubscription()

      // wait until subscription has been seen
      val probe = testKit.createTestProbe[TopicImpl.TopicStats]()
      probe.awaitAssert {
        topic ! TopicImpl.GetTopicStats(probe.ref)
        probe.expectMessageType[TopicImpl.TopicStats].localSubscriberCount should ===(1)
      }

      topic ! Topic.Publish("published")
      sourceProbe.requestNext("published")
      sourceProbe.cancel()
    }

  }

  "PubSub.sink" should {
    "publish messages" in {
      val topic = testKit.spawn(Topic[String]("my-topic-2"))

      val subscriberProbe = testKit.createTestProbe[String]()
      topic ! Topic.Subscribe(subscriberProbe.ref)

      // wait until subscription has been seen
      val probe = testKit.createTestProbe[TopicImpl.TopicStats]()
      probe.awaitAssert {
        topic ! TopicImpl.GetTopicStats(probe.ref)
        probe.expectMessageType[TopicImpl.TopicStats].localSubscriberCount should ===(1)
      }

      Source.single("published").runWith(PubSub.sink(topic))

      subscriberProbe.expectMessage("published")
    }
  }

}
