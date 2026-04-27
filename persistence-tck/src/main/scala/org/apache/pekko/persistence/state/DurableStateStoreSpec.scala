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

package org.apache.pekko.persistence.state

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.persistence._
import pekko.persistence.scalatest.{ MayVerb, OptionalTests }
import pekko.persistence.state.scaladsl.DurableStateUpdateStore
import pekko.testkit.TestProbe

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object DurableStateStoreSpec {
  val config: Config = ConfigFactory.parseString(s"""
    pekko.actor {
      serializers {
        durable-state-tck-test = "${classOf[TestSerializer].getName}"
      }
      serialization-bindings {
        "${classOf[TestPayload].getName}" = durable-state-tck-test
      }
    }
    """)
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

  override protected def supportsUpsertWithRevisionCheck: CapabilityFlag = CapabilityFlag.off()

  override protected def supportsSerialization: CapabilityFlag = CapabilityFlag.on()

  override protected def supportsSoftDelete: CapabilityFlag = CapabilityFlag.off()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    preparePersistenceId(pid)
  }

  /**
   * Overridable hook that is called before each test case.
   * `pid` is the `persistenceId` that will be used in the test.
   * This method may be needed to clean any pre-existing state from the store,
   * for example when running against a shared external database.
   */
  def preparePersistenceId(@nowarn("msg=never used") pid: String): Unit = ()

  /**
   * Returns the `DurableStateUpdateStore` under test. By default, this uses the plugin
   * configured under `pekko.persistence.state.plugin` in the provided config.
   */
  def durableStateStore(): DurableStateUpdateStore[Any] =
    DurableStateStoreRegistry(system).durableStateStoreFor[DurableStateUpdateStore[Any]]("")

  protected val timeout: FiniteDuration = 5.seconds

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

    "upsert again after a deletion" in {
      val store = durableStateStore()
      val original = s"state-${pid}"
      val recreated = s"state-${pid}-v2"
      Await.result(store.upsertObject(pid, 1L, original, "test-tag"), timeout)
      Await.result(store.deleteObject(pid, 2L), timeout)
      Await.result(store.upsertObject(pid, 3L, recreated, "test-tag"), timeout)
      val result = Await.result(store.getObject(pid), timeout)
      result.value shouldBe Some(recreated)
      result.revision shouldBe 3L
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

    optional(flag = supportsUpsertWithRevisionCheck) {
      "fail to upsert a state when the revision is stale" in {
        val store = durableStateStore()
        val original = s"state-${pid}"
        val stale = s"state-${pid}-stale"
        Await.result(store.upsertObject(pid, 1L, original, "test-tag"), timeout)
        // Re-using revision 1 should be rejected; the next valid revision is 2.
        val staleUpsert = store.upsertObject(pid, 1L, stale, "test-tag")
        intercept[Exception] {
          Await.result(staleUpsert, timeout)
        }
        // The original state should still be accessible
        val result = Await.result(store.getObject(pid), timeout)
        result.value shouldBe Some(original)
        result.revision shouldBe 1L
      }
    }

    optional(flag = supportsSerialization) {
      "serialize and deserialize values via the configured serializer" in {
        val store = durableStateStore()
        val probe = TestProbe()
        val value = TestPayload(probe.ref)
        Await.result(store.upsertObject(pid, 1L, value, "test-tag"), timeout)
        val result = Await.result(store.getObject(pid), timeout)
        result.value shouldBe Some(value)
        result.revision shouldBe 1L
      }
    }

    optional(flag = supportsSoftDelete) {
      "delete a state via the deprecated deleteObject overload" in {
        val store = durableStateStore()
        val value = s"state-${pid}"
        Await.result(store.upsertObject(pid, 1L, value, "test-tag"), timeout)
        @nowarn("cat=deprecation")
        val deleteResult = store.deleteObject(pid)
        Await.result(deleteResult, timeout)
        val result = Await.result(store.getObject(pid), timeout)
        result.value shouldBe None
      }
    }
  }
}
