/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package scala.docs.cluster

import org.apache.pekko.cluster.Cluster

import org.apache.pekko.testkit.AkkaSpec
import docs.CompileOnlySpec

object ClusterDocSpec {

  val config =
    """
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port = 0
    """
}

class ClusterDocSpec extends AkkaSpec(ClusterDocSpec.config) with CompileOnlySpec {

  "demonstrate leave" in compileOnlySpec {
    // #leave
    val cluster = Cluster(system)
    cluster.leave(cluster.selfAddress)
    // #leave
  }

  "demonstrate data center" in compileOnlySpec {
    {
      // #dcAccess
      val cluster = Cluster(system)
      // this node's data center
      val dc = cluster.selfDataCenter
      // all known data centers
      val allDc = cluster.state.allDataCenters
      // a specific member's data center
      val aMember = cluster.state.members.head
      val aDc = aMember.dataCenter
      // #dcAccess
    }
  }

  "demonstrate programmatic joining to seed nodes" in compileOnlySpec {
    // #join-seed-nodes
    import org.apache.pekko
    import pekko.actor.Address
    import pekko.cluster.Cluster

    val cluster = Cluster(system)
    val list: List[Address] = ??? // your method to dynamically get seed nodes
    cluster.joinSeedNodes(list)
    // #join-seed-nodes
  }

}
