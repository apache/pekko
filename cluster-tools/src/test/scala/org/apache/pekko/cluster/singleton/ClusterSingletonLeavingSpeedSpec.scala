/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.singleton

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.CoordinatedShutdown
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.cluster.singleton.ClusterSingletonLeavingSpeedSpec.TheSingleton
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe

object ClusterSingletonLeavingSpeedSpec {

  object TheSingleton {
    def props(probe: ActorRef): Props =
      Props(new TheSingleton(probe))
  }

  class TheSingleton(probe: ActorRef) extends Actor {
    probe ! "started"

    override def postStop(): Unit = {
      probe ! "stopped"
    }

    override def receive: Receive = {
      case msg => sender() ! msg
    }
  }
}

class ClusterSingletonLeavingSpeedSpec
    extends PekkoSpec(
      """
  pekko.loglevel = DEBUG
  pekko.actor.provider = org.apache.pekko.cluster.ClusterActorRefProvider
  pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
  pekko.cluster.testkit.auto-down-unreachable-after = 2s

  # With 10 systems and setting min-number-of-hand-over-retries to 5 and gossip-interval to 2s it's possible to
  # reproduce the ClusterSingletonManagerIsStuck and slow hand over in issue #25639
  # pekko.cluster.singleton.min-number-of-hand-over-retries = 5
  # pekko.cluster.gossip-interval = 2s

  pekko.remote {
    classic.netty.tcp {
      hostname = "127.0.0.1"
      port = 0
    }
    artery.canonical {
      hostname = "127.0.0.1"
      port = 0
    }
  }
  """) {

  private val systems = (1 to 3).map { n =>
    val roleConfig = ConfigFactory.parseString(s"""pekko.cluster.roles=[role-${n % 3}]""")
    ActorSystem(system.name, roleConfig.withFallback(system.settings.config))
  }
  private val probes = systems.map(TestProbe()(_))

  override def expectedTestDuration: FiniteDuration = 10.minutes

  import pekko.util.ccompat._
  @ccompatUsedUntil213
  def join(from: ActorSystem, to: ActorSystem, probe: ActorRef): Unit = {

    from.actorOf(
      ClusterSingletonManager.props(
        singletonProps = TheSingleton.props(probe),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(from)),
      name = "echo")

    Cluster(from).join(Cluster(to).selfAddress)
    within(15.seconds) {

      awaitAssert {
        Cluster(from).state.members.map(_.uniqueAddress) should contain(Cluster(from).selfUniqueAddress)
        Cluster(from).state.members.unsorted.map(_.status) should ===(Set(MemberStatus.Up))
      }
    }
  }

  "ClusterSingleton that is leaving" must {
    "join cluster" in {
      systems.indices.foreach { i =>
        join(systems(i), systems.head, probes(i).ref)
      }
      // leader is most likely on system, lowest port
      join(system, systems.head, testActor)

      probes(0).expectMsg("started")
    }

    "quickly hand-over to next oldest" in {

      val durations = systems.indices.take(1).map { i =>
        val t0 = System.nanoTime()
        val leaveAddress = Cluster(systems(i)).selfAddress
        CoordinatedShutdown(systems(i)).run(CoordinatedShutdown.ClusterLeavingReason)
        probes(i).expectMsg(10.seconds, "stopped")
        val stoppedDuration = (System.nanoTime() - t0).nanos
        val startedProbe = if (i == systems.size - 1) this else probes(i + 1)
        startedProbe.expectMsg(30.seconds, "started")
        val startedDuration = (System.nanoTime() - t0).nanos

        within(15.seconds) {
          awaitAssert {
            Cluster(systems(i)).isTerminated should ===(true)
            Cluster(system).state.members.map(_.address) should not contain leaveAddress
            systems.foreach { sys =>
              if (!Cluster(sys).isTerminated)
                Cluster(sys).state.members.map(_.address) should not contain leaveAddress
            }
          }
        }

        println(
          s"Singleton $i stopped in ${stoppedDuration.toMillis} ms, started in ${startedDuration.toMillis} ms, " +
          s"diff ${(startedDuration - stoppedDuration).toMillis} ms")

        (stoppedDuration, startedDuration)
      }

      durations.zipWithIndex.foreach {
        case ((stoppedDuration, startedDuration), i) =>
          println(
            s"Singleton $i stopped in ${stoppedDuration.toMillis} ms, started in ${startedDuration.toMillis} ms, " +
            s"diff ${(startedDuration - stoppedDuration).toMillis} ms")
      }

    }
  }

  override def afterTermination(): Unit = {
    systems.foreach(shutdown(_))
  }
}
