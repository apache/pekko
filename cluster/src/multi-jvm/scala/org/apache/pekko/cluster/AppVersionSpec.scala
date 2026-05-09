/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko.remote.testkit.MultiNodeConfig
import org.apache.pekko.util.Version

object AppVersionMultiJvmSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  commonConfig(debugConfig(on = false).withFallback(MultiNodeClusterSpec.clusterConfigWithFailureDetectorPuppet))
}

class AppVersionMultiJvmNode1 extends AppVersionSpec
class AppVersionMultiJvmNode2 extends AppVersionSpec

abstract class AppVersionSpec extends MultiNodeClusterSpec(AppVersionMultiJvmSpec) {

  import AppVersionMultiJvmSpec._

  "Later appVersion" must {
    "be used when joining" in {
      val laterVersion = Promise[Version]()
      cluster.setAppVersionLater(laterVersion.future)
      // ok to try to join immediately
      runOn(first) {
        cluster.join(first)
        // not joining until laterVersion has been completed
        val until = Deadline.now + 300.milliseconds
        while (!until.isOverdue()) {
          cluster.selfMember.status should ===(MemberStatus.Removed)
          Thread.sleep(50)
        }
        laterVersion.trySuccess(Version("2"))
        awaitAssert {
          cluster.selfMember.status should ===(MemberStatus.Up)
          cluster.selfMember.appVersion should ===(Version("2"))
        }
      }
      enterBarrier("first-joined")

      runOn(second) {
        cluster.joinSeedNodes(List(address(first), address(second)))
        // not joining until laterVersion has been completed
        val until = Deadline.now + 300.milliseconds
        while (!until.isOverdue()) {
          cluster.selfMember.status should ===(MemberStatus.Removed)
          Thread.sleep(50)
        }
        laterVersion.trySuccess(Version("3"))
        awaitAssert {
          cluster.selfMember.status should ===(MemberStatus.Up)
          cluster.selfMember.appVersion should ===(Version("3"))
        }
      }
      enterBarrier("second-joined")

      cluster.state.members.find(_.address == address(first)).get.appVersion should ===(Version("2"))
      cluster.state.members.find(_.address == address(second)).get.appVersion should ===(Version("3"))

      enterBarrier("after-1")
    }
  }
}
