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

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.DeadLetter
import pekko.actor.Dropped
import pekko.actor.testkit.typed.scaladsl.FishingOutcomes
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit

import org.scalatest.wordspec.AnyWordSpecLike

class LocalPubSubSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "A pub-sub topic running locally" must {

    "publish to all local subscriber actors of a topic" in {
      val fruitTopic = testKit.spawn(Topic[String]("fruit1"))

      try {
        val probe1 = testKit.createTestProbe[String]()
        val probe2 = testKit.createTestProbe[String]()
        val probe3 = testKit.createTestProbe[String]()

        fruitTopic ! Topic.Subscribe(probe1.ref)
        fruitTopic ! Topic.Subscribe(probe2.ref)
        fruitTopic ! Topic.Subscribe(probe3.ref)

        // the subscriber registration of all 3 completed
        val statsProbe = testKit.createTestProbe[Topic.TopicStats]()
        statsProbe.awaitAssert {
          fruitTopic ! Topic.GetTopicStats(statsProbe.ref)
          val stats = statsProbe.receiveMessage()
          stats.localSubscriberCount should ===(3)
          stats.topicInstanceCount should ===(1)
        }

        fruitTopic ! Topic.Publish("banana")
        probe1.expectMessage("banana")
        probe2.expectMessage("banana")
        probe3.expectMessage("banana")

      } finally {
        testKit.stop(fruitTopic)
      }
    }

    "publish to all subscriber actors across several instances of the same topic" in {
      // using different topic name than in the previous test to avoid interference
      val fruitTopic1 = testKit.spawn(Topic[String]("fruit2"))
      val fruitTopic2 = testKit.spawn(Topic[String]("fruit2"))

      try {
        val probe1 = testKit.createTestProbe[String]()
        val probe2 = testKit.createTestProbe[String]()
        val probe3 = testKit.createTestProbe[String]()

        fruitTopic2 ! Topic.Subscribe(probe1.ref)
        fruitTopic2 ! Topic.Subscribe(probe2.ref)
        fruitTopic2 ! Topic.Subscribe(probe3.ref)

        // the subscriber registration of all 3 completed
        val statsProbe = testKit.createTestProbe[Any]()
        statsProbe.awaitAssert {
          fruitTopic2 ! Topic.GetTopicStats(statsProbe.ref)
          statsProbe.expectMessageType[Topic.TopicStats].localSubscriberCount should ===(3)
        }
        // and the other topic knows about it
        statsProbe.awaitAssert {
          fruitTopic1 ! Topic.GetTopicStats(statsProbe.ref)
          statsProbe.expectMessageType[Topic.TopicStats].topicInstanceCount should ===(1)
        }

        fruitTopic1 ! Topic.Publish("apple")
        probe1.expectMessage("apple")
        probe2.expectMessage("apple")
        probe3.expectMessage("apple")

      } finally {
        testKit.stop(fruitTopic1)
        testKit.stop(fruitTopic2)
      }
    }

    "deliver messages published to a new topic instance before its first listing to other instances" in {
      val fruitTopic1 = testKit.spawn(Topic[String]("fruit5"))

      try {
        val probe1 = testKit.createTestProbe[String]()
        fruitTopic1 ! Topic.Subscribe(probe1.ref)

        // the receptionist knows about the subscribed instance
        val statsProbe = testKit.createTestProbe[Topic.TopicStats]()
        statsProbe.awaitAssert {
          fruitTopic1 ! Topic.GetTopicStats(statsProbe.ref)
          statsProbe.receiveMessage().topicInstanceCount should ===(1)
        }

        // published right after spawning, before the new instance has seen the receptionist listing
        val fruitTopic2 = testKit.spawn(Topic[String]("fruit5"))
        try {
          fruitTopic2 ! Topic.Publish("cherry")
          fruitTopic2 ! Topic.Publish("plum")
          probe1.expectMessage("cherry")
          probe1.expectMessage("plum")

          // and messages published after the listing is seen still arrive, in order
          fruitTopic2 ! Topic.Publish("kiwi")
          probe1.expectMessage("kiwi")
        } finally {
          testKit.stop(fruitTopic2)
        }
      } finally {
        testKit.stop(fruitTopic1)
      }
    }

    "deliver messages published before the first listing to local subscribers only once" in {
      val fruitTopic = testKit.spawn(Topic[String]("fruit6"))

      try {
        val probe1 = testKit.createTestProbe[String]()
        fruitTopic ! Topic.Subscribe(probe1.ref)
        fruitTopic ! Topic.Publish("lemon")
        probe1.expectMessage("lemon")

        // the listing including this instance itself arrives, the message must not be forwarded to it again
        val statsProbe = testKit.createTestProbe[Topic.TopicStats]()
        statsProbe.awaitAssert {
          fruitTopic ! Topic.GetTopicStats(statsProbe.ref)
          statsProbe.receiveMessage().topicInstanceCount should ===(1)
        }
        fruitTopic ! Topic.Publish("lime")
        probe1.expectMessage("lime")
        probe1.expectNoMessage(100.millis)
      } finally {
        testKit.stop(fruitTopic)
      }
    }

    "drop messages published before the first listing when there are no subscribers" in {
      val deadLetterProbe = testKit.createDeadLetterProbe()
      val fruitTopic = testKit.spawn(Topic[String]("fruit7"))

      try {
        fruitTopic ! Topic.Publish("melon")
        deadLetterProbe.fishForMessagePF(deadLetterProbe.remainingOrDefault) {
          case DeadLetter(Dropped("melon", _, _, _), _, _) => FishingOutcomes.complete
          case _                                           => FishingOutcomes.continueAndIgnore
        }
      } finally {
        testKit.stop(fruitTopic)
      }
    }

    "doesn't publish across topics unsubscribe" in {
      val fruitTopic =
        testKit.spawn(Topic[String]("fruit3"))

      val veggieTopic =
        testKit.spawn(Topic[String]("veggies"))

      try {
        val probe1 = testKit.createTestProbe[String]()

        fruitTopic ! Topic.Subscribe(probe1.ref)
        // the subscriber registration of all 3 completed
        val statsProbe = testKit.createTestProbe[Any]()
        statsProbe.awaitAssert {
          fruitTopic ! Topic.GetTopicStats(statsProbe.ref)
          statsProbe.expectMessageType[Topic.TopicStats].localSubscriberCount should ===(1)
        }

        // the other topic should not know about it
        statsProbe.awaitAssert {
          veggieTopic ! Topic.GetTopicStats(statsProbe.ref)
          statsProbe.expectMessageType[Topic.TopicStats].topicInstanceCount should ===(0)
        }

        veggieTopic ! Topic.Publish("carrot")
        probe1.expectNoMessage(200.millis)

      } finally {
        testKit.stop(fruitTopic)
      }
    }

    "doesn't publish after unsubscribe" in {
      val fruitTopic = testKit.spawn(Topic[String]("fruit4"))

      try {
        val probe1 = testKit.createTestProbe[String]()

        fruitTopic ! Topic.Subscribe(probe1.ref)
        // the subscriber registration of completed
        val statsProbe = testKit.createTestProbe[Any]()
        statsProbe.awaitAssert {
          fruitTopic ! Topic.GetTopicStats(statsProbe.ref)
          statsProbe.expectMessageType[Topic.TopicStats].localSubscriberCount should ===(1)
        }

        fruitTopic ! Topic.Unsubscribe(probe1.ref)
        statsProbe.awaitAssert {
          fruitTopic ! Topic.GetTopicStats(statsProbe.ref)
          statsProbe.expectMessageType[Topic.TopicStats].localSubscriberCount should ===(0)
        }

        fruitTopic ! Topic.Publish("orange")
        probe1.expectNoMessage(200.millis)

      } finally {
        testKit.stop(fruitTopic)
      }
    }

  }
}
