/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.actor.typed.internal.pubsub

import org.apache.pekko
import pekko.actor.DeadLetter
import pekko.actor.Dropped
import pekko.actor.testkit.typed.internal.BehaviorTestKitImpl
import pekko.actor.testkit.typed.scaladsl.ActorTestKit
import pekko.actor.testkit.typed.scaladsl.BehaviorTestKit
import pekko.actor.testkit.typed.scaladsl.FishingOutcomes
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.TestInbox
import pekko.actor.typed.internal.pubsub.TopicImpl._
import pekko.actor.typed.pubsub.Topic
import pekko.actor.typed.scaladsl.Behaviors

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class TopicImplSpec extends AnyWordSpec with Matchers with LogCapturing {

  private def config(window: String, bufferSize: Int = 1000): Config =
    ConfigFactory
      .parseString(s"""
        pekko.actor.typed.pubsub.startup-replay-window = $window
        pekko.actor.typed.pubsub.startup-replay-buffer-size = $bufferSize
        """)
      .withFallback(ConfigFactory.load())

  private def topic(window: String = "3s", bufferSize: Int = 1000): BehaviorTestKit[Command[String]] =
    BehaviorTestKit(
      Behaviors.setup[Command[String]](ctx => new TopicImpl[String]("fruit", ctx)),
      "topic",
      config(window, bufferSize))

  // Sending `Dropped` needs a classic sender, which the BehaviorTestKit doesn't provide, so a drop makes
  // the BehaviorTestKit tests fail with an exception. Drops are tested with a real actor system below.
  private def assertNothingDropped(kit: BehaviorTestKit[Command[String]]): Unit =
    kit.asInstanceOf[BehaviorTestKitImpl[Command[String]]].context.system.deadLettersInbox.hasSomething shouldBe false

  private def instance(): TestInbox[Command[String]] = TestInbox[Command[String]]()

  private def withActorTestKit(cfg: Config)(test: ActorTestKit => Unit): Unit = {
    val testKit = ActorTestKit("TopicImplSpec", cfg)
    try test(testKit)
    finally testKit.shutdownTestKit()
  }

  private def expectDropped(testKit: ActorTestKit, message: String)(publish: => Unit): Unit = {
    val deadLetterProbe = testKit.createDeadLetterProbe()
    publish
    deadLetterProbe.fishForMessagePF(deadLetterProbe.remainingOrDefault) {
      case DeadLetter(Dropped(`message`, _, _, _), _, _) => FishingOutcomes.complete
      case DeadLetter(Dropped(other, _, _, _), _, _)     => FishingOutcomes.fail(s"Unexpected drop of [$other]")
      case _                                             => FishingOutcomes.continueAndIgnore
    }
  }

  "A pub-sub topic" must {

    "forward messages published before its first listing to the other topic instances in it" in {
      val kit = topic(window = "0s")
      val other = instance()

      kit.run(Publish("apple"))
      kit.run(Publish("pear"))
      other.hasMessages shouldBe false

      kit.run(TopicInstancesUpdated(Set(other.ref, kit.ref)))
      other.receiveAll() shouldBe Seq(MessagePublished("apple"), MessagePublished("pear"))

      kit.run(Publish("plum"))
      other.receiveAll() shouldBe Seq(MessagePublished("plum"))
      kit.selfInbox().hasMessages shouldBe false
      assertNothingDropped(kit)
    }

    "deliver to local subscribers directly and exactly once" in {
      val kit = topic(window = "0s")
      val subscriber = TestInbox[String]()

      kit.run(Subscribe(subscriber.ref))
      kit.run(Publish("lemon"))
      subscriber.receiveAll() shouldBe Seq("lemon")

      // the listing including this instance itself must not lead to the message being delivered again
      kit.run(TopicInstancesUpdated(Set(kit.ref)))
      subscriber.hasMessages shouldBe false

      kit.run(Publish("lime"))
      subscriber.receiveAll() shouldBe Seq("lime")
      kit.selfInbox().hasMessages shouldBe false
    }

    "deliver kept messages to topic instances that become known during the startup replay window" in {
      val kit = topic()
      val other1 = instance()
      val other2 = instance()
      val other3 = instance()

      kit.run(Publish("apple"))
      // first listing from a cluster receptionist that doesn't know the other nodes yet
      kit.run(TopicInstancesUpdated(Set.empty))
      assertNothingDropped(kit)

      kit.run(TopicInstancesUpdated(Set(other1.ref)))
      other1.receiveAll() shouldBe Seq(MessagePublished("apple"))

      kit.run(Publish("pear"))
      other1.receiveAll() shouldBe Seq(MessagePublished("pear"))

      kit.run(TopicInstancesUpdated(Set(other1.ref, other2.ref)))
      other2.receiveAll() shouldBe Seq(MessagePublished("apple"), MessagePublished("pear"))
      other1.hasMessages shouldBe false

      // an instance that leaves and comes back doesn't get the kept messages again
      kit.run(TopicInstancesUpdated(Set(other2.ref)))
      kit.run(TopicInstancesUpdated(Set(other1.ref, other2.ref)))
      other1.hasMessages shouldBe false

      kit.run(StartupReplayWindowElapsed())
      kit.run(TopicInstancesUpdated(Set(other1.ref, other2.ref, other3.ref)))
      other3.hasMessages shouldBe false
      assertNothingDropped(kit)
    }

    "deliver kept messages to local subscribers that subscribe during the startup replay window" in {
      val kit = topic()
      val subscriber1 = TestInbox[String]()
      val subscriber2 = TestInbox[String]()

      kit.run(Publish("cherry"))
      kit.run(Subscribe(subscriber1.ref))
      subscriber1.receiveAll() shouldBe Seq("cherry")

      kit.run(TopicInstancesUpdated(Set(kit.ref)))
      kit.run(StartupReplayWindowElapsed())
      kit.run(Subscribe(subscriber2.ref))
      subscriber2.hasMessages shouldBe false
      assertNothingDropped(kit)
    }

    "wait for the first listing before closing the startup replay window" in {
      val kit = topic()
      val other = instance()

      kit.run(Publish("melon"))
      kit.run(StartupReplayWindowElapsed())
      assertNothingDropped(kit)

      kit.run(TopicInstancesUpdated(Set(other.ref)))
      other.receiveAll() shouldBe Seq(MessagePublished("melon"))
      assertNothingDropped(kit)
    }

    "not drop messages beyond the replay buffer size that reached a subscriber" in {
      val kit = topic(bufferSize = 2)
      val subscriber = TestInbox[String]()
      val other = instance()

      kit.run(Subscribe(subscriber.ref))
      kit.run(Publish("a"))
      kit.run(Publish("b"))
      kit.run(Publish("c"))
      subscriber.receiveAll() shouldBe Seq("a", "b", "c")
      assertNothingDropped(kit)

      kit.run(TopicInstancesUpdated(Set(other.ref)))
      other.receiveAll() shouldBe Seq(MessagePublished("a"), MessagePublished("b"))
    }

    "drop kept messages that reached no subscriber when the startup replay window closes" in {
      withActorTestKit(config(window = "200ms")) { testKit =>
        val fruitTopic = testKit.spawn(Topic[String]("melons"))
        expectDropped(testKit, "melon") {
          fruitTopic ! Topic.Publish("melon")
        }
      }
    }

    "drop messages beyond the replay buffer size that reached no subscriber right away" in {
      withActorTestKit(config(window = "1h", bufferSize = 1)) { testKit =>
        val fruitTopic = testKit.spawn(Topic[String]("grapes"))
        expectDropped(testKit, "grape2") {
          fruitTopic ! Topic.Publish("grape1")
          fruitTopic ! Topic.Publish("grape2")
        }
      }
    }
  }
}
