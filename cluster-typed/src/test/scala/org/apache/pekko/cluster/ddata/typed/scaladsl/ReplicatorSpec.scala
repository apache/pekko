/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata.typed.scaladsl

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit

class ReplicatorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {
  "Replicator" must {
    "have the prefixed replicator name" in {
      ReplicatorSettings.name(system) should ===("typedDdataReplicator")
    }
  }
}
