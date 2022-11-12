/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import pekko.persistence.testkit.{ PersistenceTestKitPlugin, PersistenceTestKitSnapshotPlugin }
import org.scalatest.concurrent.Eventually
import org.scalatest.wordspec.AnyWordSpecLike

object ReplicationBaseSpec {
  val R1 = ReplicaId("R1")
  val R2 = ReplicaId("R2")
  val AllReplicas = Set(R1, R2)
}

abstract class ReplicationBaseSpec
    extends ScalaTestWithActorTestKit(
      PersistenceTestKitPlugin.config.withFallback(PersistenceTestKitSnapshotPlugin.config))
    with AnyWordSpecLike
    with LogCapturing
    with Eventually {

  val ids = new AtomicInteger(0)
  def nextEntityId: String = s"e-${ids.getAndIncrement()}"

}
