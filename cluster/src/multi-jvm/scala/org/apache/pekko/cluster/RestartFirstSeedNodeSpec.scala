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

package org.apache.pekko.cluster

import scala.collection.immutable
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorSystem
import pekko.actor.Address
import pekko.actor.Deploy
import pekko.actor.Props
import pekko.actor.RootActorPath
import pekko.cluster.MemberStatus._
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.testkit._
import pekko.util.ccompat._

@ccompatUsedUntil213
object RestartFirstSeedNodeMultiJvmSpec extends MultiNodeConfig {
  val seed1 = role("seed1")
  val seed2 = role("seed2")
  val seed3 = role("seed3")

  commonConfig(
    debugConfig(on = false)
      .withFallback(ConfigFactory.parseString("""
      pekko.cluster.testkit.auto-down-unreachable-after = off
      pekko.cluster.retry-unsuccessful-join-after = 3s
      pekko.cluster.allow-weakly-up-members = off
      """))
      .withFallback(MultiNodeClusterSpec.clusterConfig))
}

class RestartFirstSeedNodeMultiJvmNode1 extends RestartFirstSeedNodeSpec
class RestartFirstSeedNodeMultiJvmNode2 extends RestartFirstSeedNodeSpec
class RestartFirstSeedNodeMultiJvmNode3 extends RestartFirstSeedNodeSpec

abstract class RestartFirstSeedNodeSpec
    extends MultiNodeClusterSpec(RestartFirstSeedNodeMultiJvmSpec)
    with ImplicitSender {

  import RestartFirstSeedNodeMultiJvmSpec._

  @volatile var seedNode1Address: Address = _

  // use a separate ActorSystem, to be able to simulate restart
  lazy val seed1System = ActorSystem(system.name, MultiNodeSpec.configureNextPortIfFixed(system.settings.config))

  override def verifySystemShutdown: Boolean = true

  def missingSeed = address(seed3).copy(port = Some(61313))
  def seedNodes: immutable.IndexedSeq[Address] = Vector(seedNode1Address, seed2, seed3, missingSeed)

  lazy val restartedSeed1System = ActorSystem(
    system.name,
    ConfigFactory.parseString(s"""
        pekko.remote.classic.netty.tcp.port = ${seedNodes.head.port.get}
        pekko.remote.artery.canonical.port = ${seedNodes.head.port.get}
        """).withFallback(system.settings.config))

  override def afterAll(): Unit = {
    runOn(seed1) {
      shutdown(if (seed1System.whenTerminated.isCompleted) restartedSeed1System else seed1System)

    }
    super.afterAll()
  }

  "Cluster seed nodes" must {
    "be able to restart first seed node and join other seed nodes" taggedAs LongRunningTest in within(40.seconds) {
      // seed1System is a separate ActorSystem, to be able to simulate restart
      // we must transfer its address to seed2 and seed3
      runOn(seed2, seed3) {
        system.actorOf(Props(new Actor {
            def receive = {
              case a: Address =>
                seedNode1Address = a
                sender() ! "ok"
            }
          }).withDeploy(Deploy.local), name = "address-receiver")
        enterBarrier("seed1-address-receiver-ready")
      }

      runOn(seed1) {
        enterBarrier("seed1-address-receiver-ready")
        seedNode1Address = Cluster(seed1System).selfAddress
        List(seed2, seed3).foreach { r =>
          system.actorSelection(RootActorPath(r) / "user" / "address-receiver") ! seedNode1Address
          expectMsg(5.seconds, "ok")
        }
      }
      enterBarrier("seed1-address-transferred")

      // now we can join seed1System, seed2, seed3 together
      runOn(seed1) {
        Cluster(seed1System).joinSeedNodes(seedNodes)
        awaitAssert(Cluster(seed1System).readView.members.size should ===(3))
        awaitAssert(Cluster(seed1System).readView.members.unsorted.map(_.status) should ===(Set(Up)))
      }
      runOn(seed2, seed3) {
        cluster.joinSeedNodes(seedNodes)
        awaitMembersUp(3)
      }
      enterBarrier("started")

      // shutdown seed1System
      runOn(seed1) {
        shutdown(seed1System, remainingOrDefault)
      }
      enterBarrier("seed1-shutdown")

      // then start restartedSeed1System, which has the same address as seed1System
      runOn(seed1) {
        Cluster(restartedSeed1System).joinSeedNodes(seedNodes)
        within(20.seconds) {
          awaitAssert(Cluster(restartedSeed1System).readView.members.size should ===(3))
          awaitAssert(Cluster(restartedSeed1System).readView.members.unsorted.map(_.status) should ===(Set(Up)))
        }
      }
      runOn(seed2, seed3) {
        awaitMembersUp(3)
      }
      enterBarrier("seed1-restarted")

    }

  }
}
