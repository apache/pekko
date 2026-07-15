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

import org.apache.pekko

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.state.DurableStateStoreRegistry
import pekko.persistence.state.scaladsl.DurableStateUpdateStore
import pekko.persistence.testkit.state.PersistenceTestKitDurableStateStoreProvider
import pekko.persistence.typed.PersistenceId

object RuntimeDurableStateStoreSpec {

  private object Actor {
    sealed trait Command
    final case class Save(text: String, replyTo: ActorRef[Done]) extends Command
    final case class ShowMeWhatYouGot(replyTo: ActorRef[String]) extends Command
    case object Stop extends Command

    def apply(persistenceId: String, store: String): Behavior[Command] =
      DurableStateBehavior[Command, String](
        PersistenceId.ofUniqueId(persistenceId),
        "",
        (state, cmd) =>
          cmd match {
            case Save(text, replyTo) =>
              Effect.persist(Seq(state, text).filter(_.nonEmpty).mkString("|")).thenRun(_ => replyTo ! Done)
            case ShowMeWhatYouGot(replyTo) =>
              replyTo ! state
              Effect.none
            case Stop =>
              Effect.stop()
          })
        .withDurableStateStorePluginId(s"$store.state")
        .withDurableStateStorePluginConfig(Some(config(store)))
  }

  private def config(store: String): Config =
    ConfigFactory.parseString(s"""
      $store {
        state.class = "${classOf[PersistenceTestKitDurableStateStoreProvider].getName}"
      }
    """)
}

class RuntimeDurableStateStoreSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  import RuntimeDurableStateStoreSpec._

  "The durable state store plugin" must {

    "be possible to configure at runtime and use in multiple isolated instances" in {
      val probe = createTestProbe[Any]()

      {
        // one actor in each store with same id
        val s1 = spawn(Actor("id1", "store1"))
        val s2 = spawn(Actor("id1", "store2"))
        s1 ! Actor.Save("s1m1", probe.ref)
        probe.receiveMessage()
        s2 ! Actor.Save("s2m1", probe.ref)
        probe.receiveMessage()
      }

      {
        def assertStore(store: String, expectedState: String) = {
          val durableStateStore = DurableStateStoreRegistry(system)
            .durableStateStoreFor[DurableStateUpdateStore[String]](s"$store.state", config(store))
          durableStateStore.getObject("id1").futureValue.value shouldBe Some(expectedState)
        }

        assertStore("store1", "s1m1")
        assertStore("store2", "s2m1")
      }
    }
  }
}
