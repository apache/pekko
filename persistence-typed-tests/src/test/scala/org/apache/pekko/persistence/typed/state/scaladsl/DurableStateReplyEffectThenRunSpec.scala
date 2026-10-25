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

package org.apache.pekko.persistence.typed.state.scaladsl

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit, TestProbe }
import pekko.actor.typed.{ ActorRef, Behavior }
import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin
import pekko.persistence.typed.PersistenceId

import org.scalatest.wordspec.AnyWordSpecLike

class DurableStateReplyEffectThenRunSpec
    extends ScalaTestWithActorTestKit(PersistenceTestKitDurableStateStorePlugin.config)
    with AnyWordSpecLike
    with LogCapturing {

  private val ids = new AtomicInteger()

  private def behavior[Command](handler: (Int, Command) => ReplyEffect[Int]): Behavior[Command] =
    DurableStateBehavior.withEnforcedReplies[Command, Int](
      PersistenceId.ofUniqueId(s"then-run-${ids.incrementAndGet()}"),
      emptyState = 0,
      commandHandler = handler)

  private def observe[Self <: ReplyEffectOps[Int, Self]](
      effect: ReplyEffectOps[Int, Self], callback: Int => Unit): Self =
    effect.thenRun(callback)

  "A reply-effect stage" must {
    "run callbacks in order after replying with the persisted state and before stopping" in {
      val probe = TestProbe[String]()
      val actor = spawn(behavior[ActorRef[String]] { (_, replyTo) =>
        Effect.persist(1)
          .thenReply(replyTo)(state => s"reply:$state")
          .thenRun(state => replyTo ! s"callback:$state")
          .thenRun(state => replyTo ! s"another:$state")
          .thenStop()
      })
      actor ! probe.ref
      probe.expectMessage("reply:1")
      probe.expectMessage("callback:1")
      probe.expectMessage("another:1")
      probe.expectTerminated(actor)
    }

    "decorate a handler that accepts or rejects commands" in {
      val probe = TestProbe[String]()
      val handler: (Int, ActorRef[String]) => ReplyEffectWithStop[Int] = (state, replyTo) =>
        if (state == 0)
          Effect.persist(1).thenReply(replyTo)(updatedState => s"accepted:$updatedState")
        else
          Effect.reply(replyTo)("rejected")
      val actor = spawn(behavior[ActorRef[String]] { (state, replyTo) =>
        handler(state, replyTo).thenRun(updatedState => replyTo ! s"observed:$updatedState")
      })

      actor ! probe.ref
      probe.expectMessage("accepted:1")
      probe.expectMessage("observed:1")
      actor ! probe.ref
      probe.expectMessage("rejected")
      probe.expectMessage("observed:1")
    }

    "retain enforced replies when appending callbacks after noReply and unstashAll" in {
      val probe = TestProbe[String]()
      val actor = spawn(behavior[ActorRef[String]] { (_, replyTo) =>
        Effect.noReply[Int].thenUnstashAll().thenRun(state => replyTo ! s"callback:$state").thenStop()
      })
      actor ! probe.ref
      probe.expectMessage("callback:0")
      probe.expectTerminated(actor)
    }

    "preserve the stage through a generic callback helper after thenNoReply" in {
      val probe = TestProbe[String]()
      val actor = spawn(behavior[ActorRef[String]] { (_, replyTo) =>
        observe(Effect.persist(1).thenNoReply(), state => replyTo ! s"callback:$state")
          .thenUnstashAll()
          .thenStop()
      })
      actor ! probe.ref
      probe.expectMessage("callback:1")
      probe.expectTerminated(actor)
    }

    "finish callbacks on both sides of unstashAll before processing stashed commands" in {
      val probe = TestProbe[String]()
      val actor = spawn(behavior[String] { (state, command) =>
        if (command == "stash" && state == 0) {
          probe.ref ! "stashed"
          Effect.stash[Int]()
        } else if (command == "release") {
          Effect.persist(1)
            .thenReply(probe.ref)(updatedState => s"reply:$updatedState")
            .thenRun(updatedState => probe.ref ! s"before-unstash:$updatedState")
            .thenUnstashAll()
            .thenRun(updatedState => probe.ref ! s"after-unstash:$updatedState")
        } else {
          Effect.reply(probe.ref)(s"unstashed:$state").thenStop()
        }
      })

      actor ! "stash"
      probe.expectMessage("stashed")
      actor ! "release"
      probe.expectMessage("reply:1")
      probe.expectMessage("before-unstash:1")
      probe.expectMessage("after-unstash:1")
      probe.expectMessage("unstashed:1")
      probe.expectTerminated(actor)
    }

    "reject callbacks after stopping in both DSLs" in {
      "Effect.noReply[Int].thenRun((state: Int) => ()).thenStop()" should compile
      "Effect.noReply[Int].thenStop().thenRun((state: Int) => ())" shouldNot compile
      "Effect.noReply[Int].thenStop().thenUnstashAll().thenRun((state: Int) => ())" shouldNot compile
      "new org.apache.pekko.persistence.typed.state.javadsl.EffectFactories[Int]().noReply().thenRun(() => ()).thenStop()" should
      compile
      "new org.apache.pekko.persistence.typed.state.javadsl.EffectFactories[Int]().noReply().thenStop().thenRun(() => ())" shouldNot
      compile
      "new org.apache.pekko.persistence.typed.state.javadsl.EffectFactories[Int]().noReply().thenStop().thenRun((state: Int) => ())" shouldNot
      compile
      "new org.apache.pekko.persistence.typed.state.javadsl.EffectFactories[Int]().noReply().thenStop().thenUnstashAll().thenRun(() => ())" shouldNot
      compile
    }

    "keep callbacks unavailable after stashing in both DSLs" in {
      "Effect.stash[Int]().thenRun((state: Int) => ())" shouldNot compile
      "Effect.stash[Int]().thenUnstashAll().thenRun((state: Int) => ())" shouldNot compile
      "new org.apache.pekko.persistence.typed.state.javadsl.EffectFactories[Int]().stash().thenRun(() => ())" shouldNot
      compile
      "new org.apache.pekko.persistence.typed.state.javadsl.EffectFactories[Int]().stash().thenRun((state: Int) => ())" shouldNot
      compile
      "new org.apache.pekko.persistence.typed.state.javadsl.EffectFactories[Int]().stash().thenUnstashAll().thenRun(() => ())" shouldNot
      compile
    }

    "require helpers to retain the callback capability explicitly" in {
      "{ val effect: ReplyEffect[Int] = Effect.noReply[Int]; effect.thenRun((state: Int) => ()) }" shouldNot compile
    }
  }
}
