/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.state

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.persistence.CapabilityFlag
import pekko.persistence.DurableStateStoreCapabilityFlags
import pekko.persistence.PluginSpec
import pekko.persistence.scalatest.{ MayVerb, OptionalTests }
import pekko.persistence.state.scaladsl.DurableStateUpdateStore

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object DurableStateStoreSpec {
  val config: Config = ConfigFactory.empty()
}

/**
 * This spec aims to verify custom pekko-persistence [[DurableStateStore]] implementations.
 * Plugin authors are highly encouraged to include it in their plugin's test suites.
 *
 * In case your durable state store plugin needs some kind of setup or teardown, override the `beforeAll`
 * or `afterAll` methods (don't forget to call `super` in your overridden methods).
 *
 * For a Java and JUnit consumable version of the TCK please refer to
 * [[pekko.persistence.japi.state.JavaDurableStateStoreSpec]].
 *
 * @see [[pekko.persistence.japi.state.JavaDurableStateStoreSpec]]
 */
abstract class DurableStateStoreSpec(config: Config)
    extends PluginSpec(config)
    with MayVerb
    with OptionalTests
    with DurableStateStoreCapabilityFlags {

  implicit lazy val system: ActorSystem =
    ActorSystem("DurableStateStoreSpec", config.withFallback(DurableStateStoreSpec.config))

  override protected def supportsDeleteWithRevisionCheck: CapabilityFlag = CapabilityFlag.off()

  /**
   * Returns the `DurableStateUpdateStore` under test. By default, this uses the plugin
   * configured under `pekko.persistence.state.plugin` in the provided config.
   */
  def durableStateStore(): DurableStateUpdateStore[Any] =
    DurableStateStoreRegistry(system).durableStateStoreFor[DurableStateUpdateStore[Any]]("")

  private val timeout = 3.seconds

  "A durable state store" must {
    "not find a non-existing object" in {
      val result = Await.result(durableStateStore().getObject(pid), timeout)
      result.value shouldBe None
    }

    "persist a state and retrieve it" in {
      val value = s"state-${pid}"
      Await.result(durableStateStore().upsertObject(pid, 1L, value, "test-tag"), timeout)
      val result = Await.result(durableStateStore().getObject(pid), timeout)
      result.value shouldBe Some(value)
      result.revision shouldBe 1L
    }

    "update a state" in {
      val store = durableStateStore()
      val value1 = s"state-1-${pid}"
      val value2 = s"state-2-${pid}"
      Await.result(store.upsertObject(pid, 1L, value1, "test-tag"), timeout)
      Await.result(store.upsertObject(pid, 2L, value2, "test-tag"), timeout)
      val result = Await.result(store.getObject(pid), timeout)
      result.value shouldBe Some(value2)
      result.revision shouldBe 2L
    }

    "delete a state" in {
      val store = durableStateStore()
      val value = s"state-${pid}"
      Await.result(store.upsertObject(pid, 1L, value, "test-tag"), timeout)
      Await.result(store.deleteObject(pid, 2L), timeout)
      val result = Await.result(store.getObject(pid), timeout)
      result.value shouldBe None
    }

    "handle different persistence IDs independently" in {
      val store = durableStateStore()
      val pid2 = pid + "-2"
      val value1 = s"state-${pid}"
      val value2 = s"state-${pid2}"
      Await.result(store.upsertObject(pid, 1L, value1, "test-tag"), timeout)
      Await.result(store.upsertObject(pid2, 1L, value2, "test-tag"), timeout)

      val result1 = Await.result(store.getObject(pid), timeout)
      val result2 = Await.result(store.getObject(pid2), timeout)

      result1.value shouldBe Some(value1)
      result2.value shouldBe Some(value2)
    }
  }

  "A durable state store optionally".may {
    optional(flag = supportsDeleteWithRevisionCheck) {
      "fail to delete a state when the revision does not match" in {
        val store = durableStateStore()
        val value = s"state-${pid}"
        Await.result(store.upsertObject(pid, 1L, value, "test-tag"), timeout)
        val deleteResult = store.deleteObject(pid, 99L)
        intercept[Exception] {
          Await.result(deleteResult, timeout)
        }
        // The original state should still be accessible
        val result = Await.result(store.getObject(pid), timeout)
        result.value shouldBe Some(value)
        result.revision shouldBe 1L
      }
    }
  }
}
