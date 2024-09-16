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

import java.lang.management.ManagementFactory

import javax.management.ObjectName
import scala.concurrent.Await
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.Address
import pekko.actor.CoordinatedShutdown
import pekko.actor.ExtendedActorSystem
import pekko.actor.Props
import pekko.cluster.ClusterEvent._
import pekko.cluster.ClusterEvent.MemberEvent
import pekko.cluster.InternalClusterAction._
import pekko.stream.Materializer
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.scaladsl.StreamRefs
import pekko.testkit.PekkoSpec
import pekko.testkit.ImplicitSender
import pekko.testkit.TestProbe
import pekko.util.Version

object ClusterSpec {
  val config = """
    pekko.cluster {
      downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
      testkit.auto-down-unreachable-after = 0s
      periodic-tasks-initial-delay = 120 seconds // turn off scheduled tasks
      publish-stats-interval = 0 s # always, when it happens
      failure-detector.implementation-class = org.apache.pekko.cluster.FailureDetectorPuppet
      app-version = "1.2.3"
    }
    pekko.actor.provider = "cluster"
    pekko.remote.log-remote-lifecycle-events = off
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    """

  final case class GossipTo(address: Address)
}

class ClusterSpec extends PekkoSpec(ClusterSpec.config) with ImplicitSender {

  val selfAddress = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress

  val cluster = Cluster(system)
  def clusterView = cluster.readView

  def leaderActions(): Unit =
    cluster.clusterCore ! LeaderActionsTick

  "A Cluster" must {

    "use the address of the remote transport" in {
      cluster.selfAddress should ===(selfAddress)
    }

    "register jmx mbean" in {
      val name = new ObjectName("pekko:type=Cluster")
      val info = ManagementFactory.getPlatformMBeanServer.getMBeanInfo(name)
      info.getAttributes.length should be > 0
      info.getOperations.length should be > 0
    }

    "reply with InitJoinNack for InitJoin before joining" in {
      system.actorSelection("/system/cluster/core/daemon") ! InitJoin(system.settings.config)
      expectMsgType[InitJoinNack]
    }

    "fail fast in a join if invalid chars in host names, e.g. docker host given name" in {
      val addresses = scala.collection.immutable
        .Seq(Address("pekko", "sys", Some("in_valid"), Some(0)), Address("pekko", "sys", Some("invalid._org"), Some(0)))

      addresses.foreach(a => intercept[IllegalArgumentException](cluster.join(a)))
      intercept[IllegalArgumentException](cluster.joinSeedNodes(addresses))
    }

    "not fail fast to attempt a join with valid chars in host names" in {
      val addresses = scala.collection.immutable.Seq(
        Address("pekko", "sys", Some("localhost"), Some(0)),
        Address("pekko", "sys", Some("is_valid.org"), Some(0)),
        Address("pekko", "sys", Some("fu.is_valid.org"), Some(0)),
        Address("pekko", "sys", Some("fu_.is_valid.org"), Some(0)))

      addresses.foreach(cluster.join)
      cluster.joinSeedNodes(addresses)
    }

    "initially become singleton cluster when joining itself and reach convergence" in {
      clusterView.members.size should ===(0)
      cluster.join(selfAddress)
      leaderActions() // Joining -> Up
      awaitCond(clusterView.isSingletonCluster)
      clusterView.self.address should ===(selfAddress)
      clusterView.members.map(_.address) should ===(Set(selfAddress))
      awaitAssert(clusterView.status should ===(MemberStatus.Up))
      clusterView.self.appVersion should ===(Version("1.2.3"))
      clusterView.members.find(_.address == selfAddress).get.appVersion should ===(Version("1.2.3"))
    }

    "reply with InitJoinAck for InitJoin after joining" in {
      system.actorSelection("/system/cluster/core/daemon") ! InitJoin(system.settings.config)
      expectMsgType[InitJoinAck]
    }

    "publish initial state as snapshot to subscribers" in {
      try {
        cluster.subscribe(testActor, ClusterEvent.InitialStateAsSnapshot, classOf[ClusterEvent.MemberEvent])
        expectMsgClass(classOf[ClusterEvent.CurrentClusterState])
      } finally
        cluster.unsubscribe(testActor)
    }

    "publish initial state as events to subscribers" in {
      try {
        cluster.subscribe(testActor, ClusterEvent.InitialStateAsEvents, classOf[ClusterEvent.MemberEvent])
        expectMsgClass(classOf[ClusterEvent.MemberUp])
      } finally
        cluster.unsubscribe(testActor)
    }

    "send CurrentClusterState to one receiver when requested" in {
      cluster.sendCurrentClusterState(testActor)
      expectMsgClass(classOf[ClusterEvent.CurrentClusterState])
    }

    // this should be the last test step, since the cluster is shutdown
    "publish MemberRemoved when shutdown" in {
      cluster.subscribe(testActor, classOf[ClusterEvent.MemberRemoved])
      // first, is in response to the subscription
      expectMsgClass(classOf[ClusterEvent.CurrentClusterState])

      cluster.shutdown()
      expectMsgType[ClusterEvent.MemberRemoved].member.address should ===(selfAddress)
    }

    "allow join and leave with local address" in {
      val sys2 = ActorSystem(
        "ClusterSpec2",
        ConfigFactory.parseString("""
        pekko.actor.provider = "cluster"
        pekko.remote.classic.netty.tcp.port = 0
        pekko.remote.artery.canonical.port = 0
        """))
      try {
        val ref = sys2.actorOf(Props.empty)
        Cluster(sys2).join(ref.path.address) // address doesn't contain full address information
        within(5.seconds) {
          awaitAssert {
            Cluster(sys2).state.members.size should ===(1)
            Cluster(sys2).state.members.head.status should ===(MemberStatus.Up)
          }
        }
        Cluster(sys2).leave(ref.path.address)
        within(5.seconds) {
          awaitAssert {
            Cluster(sys2).isTerminated should ===(true)
          }
        }
      } finally
        shutdown(sys2)
    }

    "allow to resolve remotePathOf any actor" in {
      testActor.path.address.host should ===(None)
      cluster.remotePathOf(testActor).uid should ===(testActor.path.uid)
      cluster.remotePathOf(testActor).address should ===(selfAddress)
    }

    "leave via CoordinatedShutdown.run" in {
      val sys2 = ActorSystem(
        "ClusterSpec2",
        ConfigFactory.parseString("""
        pekko.actor.provider = "cluster"
        pekko.remote.classic.netty.tcp.port = 0
        pekko.remote.artery.canonical.port = 0
        """))
      try {
        val probe = TestProbe()(sys2)
        Cluster(sys2).subscribe(probe.ref, classOf[MemberEvent])
        probe.expectMsgType[CurrentClusterState]
        Cluster(sys2).join(Cluster(sys2).selfAddress)
        probe.expectMsgType[MemberUp]

        CoordinatedShutdown(sys2).run(CoordinatedShutdown.UnknownReason)
        probe.expectMsgType[MemberLeft]
        // MemberExited might not be published before MemberRemoved
        val removed = probe
          .fishForMessage() {
            case _: MemberExited  => false
            case _: MemberRemoved => true
          }
          .asInstanceOf[MemberRemoved]
        removed.previousStatus should ===(MemberStatus.Exiting)
      } finally
        shutdown(sys2)
    }

    "terminate ActorSystem via CoordinatedShutdown.run when a stream involving StreamRefs is running" in {
      val sys2 = ActorSystem(
        "ClusterSpec2",
        ConfigFactory.parseString("""
        pekko.actor.provider = "cluster"
        pekko.remote.classic.netty.tcp.port = 0
        pekko.remote.artery.canonical.port = 0
        pekko.coordinated-shutdown.terminate-actor-system = on
        """))
      try {
        val probe = TestProbe()(sys2)
        Cluster(sys2).subscribe(probe.ref, classOf[MemberEvent])
        probe.expectMsgType[CurrentClusterState]
        Cluster(sys2).join(Cluster(sys2).selfAddress)
        probe.expectMsgType[MemberUp]
        val mat = Materializer(sys2)
        val sink = StreamRefs.sinkRef[String]().to(Sink.ignore).run()(mat)
        Source.tick(1.milli, 10.millis, "tick").to(sink).run()(mat)

        CoordinatedShutdown(sys2).run(CoordinatedShutdown.UnknownReason)
        probe.expectMsgType[MemberLeft]
        // MemberExited might not be published before MemberRemoved
        val removed = probe
          .fishForMessage() {
            case _: MemberExited  => false
            case _: MemberRemoved => true
          }
          .asInstanceOf[MemberRemoved]
        removed.previousStatus should ===(MemberStatus.Exiting)

        Await.result(sys2.whenTerminated, 10.seconds)
        Cluster(sys2).isTerminated should ===(true)
        CoordinatedShutdown(sys2).shutdownReason() should ===(Some(CoordinatedShutdown.UnknownReason))
      } finally
        shutdown(sys2)
    }

    "leave via CoordinatedShutdown.run when member status is Joining" in {
      val sys2 = ActorSystem(
        "ClusterSpec2",
        ConfigFactory.parseString("""
        pekko.actor.provider = "cluster"
        pekko.remote.classic.netty.tcp.port = 0
        pekko.remote.artery.canonical.port = 0
        pekko.cluster.min-nr-of-members = 2
        """))
      try {
        val probe = TestProbe()(sys2)
        Cluster(sys2).subscribe(probe.ref, classOf[MemberEvent])
        probe.expectMsgType[CurrentClusterState]
        Cluster(sys2).join(Cluster(sys2).selfAddress)
        probe.expectMsgType[MemberJoined]

        CoordinatedShutdown(sys2).run(CoordinatedShutdown.UnknownReason)
        probe.expectMsgType[MemberLeft]
        // MemberExited might not be published before MemberRemoved
        val removed = probe
          .fishForMessage() {
            case _: MemberExited  => false
            case _: MemberRemoved => true
          }
          .asInstanceOf[MemberRemoved]
        removed.previousStatus should ===(MemberStatus.Exiting)
      } finally
        shutdown(sys2)
    }

    "terminate ActorSystem via leave (CoordinatedShutdown)" in {
      val sys2 = ActorSystem(
        "ClusterSpec2",
        ConfigFactory.parseString("""
        pekko.actor.provider = "cluster"
        pekko.remote.classic.netty.tcp.port = 0
        pekko.remote.artery.canonical.port = 0
        pekko.coordinated-shutdown.terminate-actor-system = on
        """))
      try {
        val probe = TestProbe()(sys2)
        Cluster(sys2).subscribe(probe.ref, classOf[MemberEvent])
        probe.expectMsgType[CurrentClusterState]
        Cluster(sys2).join(Cluster(sys2).selfAddress)
        probe.expectMsgType[MemberUp]

        Cluster(sys2).leave(Cluster(sys2).selfAddress)
        probe.expectMsgType[MemberLeft]
        // MemberExited might not be published before MemberRemoved
        val removed = probe
          .fishForMessage() {
            case _: MemberExited  => false
            case _: MemberRemoved => true
          }
          .asInstanceOf[MemberRemoved]
        removed.previousStatus should ===(MemberStatus.Exiting)
        Await.result(sys2.whenTerminated, 10.seconds)
        Cluster(sys2).isTerminated should ===(true)
        CoordinatedShutdown(sys2).shutdownReason() should ===(Some(CoordinatedShutdown.ClusterLeavingReason))
      } finally
        shutdown(sys2)
    }

    "terminate ActorSystem via down (CoordinatedShutdown)" in {
      val sys3 = ActorSystem(
        "ClusterSpec3",
        ConfigFactory.parseString("""
        pekko.actor.provider = "cluster"
        pekko.remote.classic.netty.tcp.port = 0
        pekko.remote.artery.canonical.port = 0
        pekko.coordinated-shutdown.terminate-actor-system = on
        pekko.cluster.run-coordinated-shutdown-when-down = on
        """))
      try {
        val probe = TestProbe()(sys3)
        Cluster(sys3).subscribe(probe.ref, classOf[MemberEvent])
        probe.expectMsgType[CurrentClusterState]
        Cluster(sys3).join(Cluster(sys3).selfAddress)
        probe.expectMsgType[MemberUp]

        Cluster(sys3).down(Cluster(sys3).selfAddress)
        probe.expectMsgType[MemberDowned]
        probe.expectMsgType[MemberRemoved]
        Await.result(sys3.whenTerminated, 10.seconds)
        Cluster(sys3).isTerminated should ===(true)
        CoordinatedShutdown(sys3).shutdownReason() should ===(Some(CoordinatedShutdown.ClusterDowningReason))
      } finally
        shutdown(sys3)
    }

    "register multiple cluster JMX MBeans with pekko.cluster.jmx.multi-mbeans-in-same-jvm = on" in {
      def getConfig = (port: Int) => ConfigFactory.parseString(s"""
             pekko.cluster.jmx.multi-mbeans-in-same-jvm = on
             pekko.remote.classic.netty.tcp.port = $port
             pekko.remote.artery.canonical.port = $port
          """).withFallback(ConfigFactory.parseString(ClusterSpec.config))

      val sys1 = ActorSystem("ClusterSpec4", getConfig(7355))
      val sys2 = ActorSystem("ClusterSpec4", getConfig(7356))

      try {
        Cluster(sys1)
        Cluster(sys2)

        val name1 = new ObjectName(s"pekko:type=Cluster,port=7355")
        val info1 = ManagementFactory.getPlatformMBeanServer.getMBeanInfo(name1)
        info1.getAttributes.length should be > 0
        info1.getOperations.length should be > 0

        val name2 = new ObjectName(s"pekko:type=Cluster,port=7356")
        val info2 = ManagementFactory.getPlatformMBeanServer.getMBeanInfo(name2)
        info2.getAttributes.length should be > 0
        info2.getOperations.length should be > 0
      } finally {
        shutdown(sys1)
        shutdown(sys2)
      }

    }
  }
}
