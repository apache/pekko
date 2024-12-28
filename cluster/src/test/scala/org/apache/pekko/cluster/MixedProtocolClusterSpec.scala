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

package org.apache.pekko.cluster

import com.typesafe.config.{ Config, ConfigFactory }

import org.apache.pekko.testkit.{ LongRunningTest, PekkoSpec }

object MixedProtocolClusterSpec {

  val baseConfig: Config =
    ConfigFactory.parseString("""
     pekko.actor.provider = "cluster"
     pekko.coordinated-shutdown.terminate-actor-system = on

     pekko.remote.artery.canonical.port = 0
     pekko.remote.artery.advanced.aeron.idle-cpu-level = 3
     pekko.remote.accept-protocol-names = ["pekko", "akka"]

     pekko.cluster.jmx.multi-mbeans-in-same-jvm = on
     pekko.cluster.configuration-compatibility-check.enforce-on-join = off
     """)

  val configWithPekko: Config =
    ConfigFactory.parseString("""
      pekko.remote.protocol-name = "pekko"
    """).withFallback(baseConfig)

  val configWithPekkoTcp: Config =
    ConfigFactory.parseString("""
      pekko.remote.classic.netty.tcp.port = 0
      pekko.remote.protocol-name = "pekko"
    """).withFallback(baseConfig)

  val configWithAkka: Config =
    ConfigFactory.parseString("""
      pekko.remote.protocol-name = "akka"
    """).withFallback(baseConfig)

  val configWithAkkaTcp: Config =
    ConfigFactory.parseString("""
      pekko.remote.classic.netty.tcp.port = 0
      pekko.remote.protocol-name = "akka"
    """).withFallback(baseConfig)
}

class MixedProtocolClusterSpec extends PekkoSpec with ClusterTestKit {

  import MixedProtocolClusterSpec._

  "A node using the akka protocol" must {

    "be allowed to join a cluster with a node using the pekko protocol" taggedAs LongRunningTest in {

      val clusterTestUtil = new ClusterTestUtil(system.name)
      // start the first node with the "pekko" protocol
      clusterTestUtil.newActorSystem(configWithPekko)

      // have a node using the "akka" protocol join
      val joiningNode = clusterTestUtil.newActorSystem(configWithAkka)
      clusterTestUtil.formCluster()

      try {
        awaitCond(clusterTestUtil.isMemberUp(joiningNode), message = "awaiting joining node to be 'Up'")
      } finally {
        clusterTestUtil.shutdownAll()
      }
    }

    "be allowed to join a cluster with a node using the pekko protocol (tcp)" taggedAs LongRunningTest in {

      val clusterTestUtil = new ClusterTestUtil(system.name)
      // start the first node with the "pekko" protocol
      clusterTestUtil.newActorSystem(configWithPekkoTcp)

      // have a node using the "akka" protocol join
      val joiningNode = clusterTestUtil.newActorSystem(configWithAkkaTcp)
      clusterTestUtil.formCluster()

      try {
        awaitCond(clusterTestUtil.isMemberUp(joiningNode), message = "awaiting joining node to be 'Up'")
      } finally {
        clusterTestUtil.shutdownAll()
      }
    }

    "allow a node using the pekko protocol to join the cluster" taggedAs LongRunningTest in {

      val clusterTestUtil = new ClusterTestUtil(system.name)

      // create the first node with the "akka" protocol
      clusterTestUtil.newActorSystem(configWithAkka)

      // have a node using the "pekko" protocol join
      val joiningNode = clusterTestUtil.newActorSystem(configWithPekko)
      clusterTestUtil.formCluster()

      try {
        awaitCond(clusterTestUtil.isMemberUp(joiningNode), message = "awaiting joining node to be 'Up'")
      } finally {
        clusterTestUtil.shutdownAll()
      }
    }

    "allow a node using the pekko protocol to join the cluster (tcp)" taggedAs LongRunningTest in {

      val clusterTestUtil = new ClusterTestUtil(system.name)

      // create the first node with the "akka" protocol
      clusterTestUtil.newActorSystem(configWithAkkaTcp)

      // have a node using the "pekko" protocol join
      val joiningNode = clusterTestUtil.newActorSystem(configWithPekkoTcp)
      clusterTestUtil.formCluster()

      try {
        awaitCond(clusterTestUtil.isMemberUp(joiningNode), message = "awaiting joining node to be 'Up'")
      } finally {
        clusterTestUtil.shutdownAll()
      }
    }
  }
}
