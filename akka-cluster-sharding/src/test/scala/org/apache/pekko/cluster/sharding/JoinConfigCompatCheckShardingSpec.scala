/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import scala.collection.{ immutable => im }
import scala.concurrent.duration._

import com.typesafe.config.{ Config, ConfigFactory }

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.cluster.{ Cluster, ClusterReadView }
import pekko.testkit.WithLogCapturing
import pekko.testkit.{ AkkaSpec, LongRunningTest }

class JoinConfigCompatCheckShardingSpec extends AkkaSpec() with WithLogCapturing {

  def initCluster(system: ActorSystem): ClusterReadView = {
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
    val clusterView = cluster.readView
    awaitCond(clusterView.isSingletonCluster)
    clusterView
  }

  val baseConfig: Config =
    ConfigFactory.parseString("""
     pekko.actor.provider = "cluster"
     pekko.loglevel = DEBUG
     pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
     pekko.coordinated-shutdown.terminate-actor-system = on
     pekko.remote.classic.netty.tcp.port = 0
     pekko.remote.artery.canonical.port = 0
     pekko.cluster.sharding.verbose-debug-logging = on
     """)

  "A Joining Node" must {

    /** This test verifies the built-in JoinConfigCompatCheckerSharding */
    "NOT be allowed to join a cluster using a different value for pekko.cluster.sharding.state-store-mode" taggedAs LongRunningTest in {

      val joinNodeConfig =
        ConfigFactory.parseString("""
              pekko.cluster {

                # use 'persistence' for state store
                sharding.state-store-mode = "persistence"

                configuration-compatibility-check {
                  enforce-on-join = on
                }
              }
            """)

      val seedNode = ActorSystem(system.name, baseConfig)
      val joiningNode = ActorSystem(system.name, joinNodeConfig.withFallback(baseConfig))

      val clusterView = initCluster(seedNode)
      val joiningNodeCluster = Cluster(joiningNode)

      try {
        // join with compatible node
        joiningNodeCluster.joinSeedNodes(im.Seq(clusterView.selfAddress))

        // node will shutdown after unsuccessful join attempt
        within(5.seconds) {
          awaitCond(joiningNodeCluster.readView.isTerminated)
        }

      } finally {
        shutdown(seedNode)
        shutdown(joiningNode)
      }

    }
  }

}
