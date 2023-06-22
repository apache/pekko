/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

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
