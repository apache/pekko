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

package org.apache.pekko.remote.testkit

import org.apache.pekko
import pekko.remote.RemotingMultiNodeSpec
import pekko.testkit.LongRunningTest

object MultiNodeSpecMultiJvmSpec extends MultiNodeConfig {
  commonConfig(debugConfig(on = false).withFallback(RemotingMultiNodeSpec.commonConfig))

  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")
  val node4 = role("node4")
}

class MultiNodeSpecSpecMultiJvmNode1 extends MultiNodeSpecSpec
class MultiNodeSpecSpecMultiJvmNode2 extends MultiNodeSpecSpec
class MultiNodeSpecSpecMultiJvmNode3 extends MultiNodeSpecSpec
class MultiNodeSpecSpecMultiJvmNode4 extends MultiNodeSpecSpec

class MultiNodeSpecSpec extends RemotingMultiNodeSpec(MultiNodeSpecMultiJvmSpec) {

  def initialParticipants = 4

  "A MultiNodeSpec" must {

    "wait for all nodes to remove themselves before we shut the conductor down" taggedAs LongRunningTest in {
      enterBarrier("startup")
      // this test is empty here since it only exercises the shutdown code in the MultiNodeSpec
    }

  }
}
