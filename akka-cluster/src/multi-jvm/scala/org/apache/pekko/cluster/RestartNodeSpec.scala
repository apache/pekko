/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import scala.collection.immutable
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.Done
import pekko.actor.Actor
import pekko.actor.ActorIdentity
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.Address
import pekko.actor.Deploy
import pekko.actor.Identify
import pekko.actor.Props
import pekko.actor.RootActorPath
import pekko.actor.Terminated
import pekko.cluster.MemberStatus._
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.testkit._
import pekko.util.ccompat._

@ccompatUsedUntil213
object RestartNodeMultiJvmSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(
    debugConfig(on = false)
      .withFallback(ConfigFactory.parseString("""
      akka.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
      akka.cluster.testkit.auto-down-unreachable-after = 5s
      akka.cluster.allow-weakly-up-members = off
      #akka.remote.use-passive-connections = off
      # test is using Java serialization and not priority to rewrite
      akka.actor.allow-java-serialization = on
      akka.actor.warn-about-java-serializer-usage = off
      """))
      .withFallback(MultiNodeClusterSpec.clusterConfig))

  /**
   * This was used together with sleep in EndpointReader before deliverAndAck
   * to reproduce issue with misaligned ACKs when restarting system,
   * issue #19780
   */
  class Watcher(a: Address, replyTo: ActorRef) extends Actor {
    context.actorSelection(RootActorPath(a) / "user" / "address-receiver") ! Identify(None)

    def receive = {
      case ActorIdentity(None, Some(ref)) =>
        context.watch(ref)
        replyTo ! Done
      case _: Terminated =>
    }
  }
}

class RestartNodeMultiJvmNode1 extends RestartNodeSpec
class RestartNodeMultiJvmNode2 extends RestartNodeSpec
class RestartNodeMultiJvmNode3 extends RestartNodeSpec

abstract class RestartNodeSpec extends MultiNodeClusterSpec(RestartNodeMultiJvmSpec) with ImplicitSender {

  import RestartNodeMultiJvmSpec._

  @volatile var secondUniqueAddress: UniqueAddress = _

  // use a separate ActorSystem, to be able to simulate restart
  lazy val secondSystem = ActorSystem(system.name, MultiNodeSpec.configureNextPortIfFixed(system.settings.config))

  override def verifySystemShutdown: Boolean = true

  def seedNodes: immutable.IndexedSeq[Address] = Vector(first, secondUniqueAddress.address, third)

  lazy val restartedSecondSystem = ActorSystem(
    system.name,
    ConfigFactory.parseString(s"""
      akka.remote.classic.netty.tcp.port = ${secondUniqueAddress.address.port.get}
      akka.remote.artery.canonical.port = ${secondUniqueAddress.address.port.get}
      """).withFallback(system.settings.config))

  override def afterAll(): Unit = {
    runOn(second) {
      if (secondSystem.whenTerminated.isCompleted)
        shutdown(restartedSecondSystem)
      else
        shutdown(secondSystem)
    }
    super.afterAll()
  }

  "Cluster nodes" must {
    "be able to restart and join again" taggedAs LongRunningTest in within(60.seconds) {
      // secondSystem is a separate ActorSystem, to be able to simulate restart
      // we must transfer its address to first
      runOn(first, third) {
        system.actorOf(Props(new Actor {
            def receive = {
              case a: UniqueAddress =>
                secondUniqueAddress = a
                sender() ! "ok"
            }
          }).withDeploy(Deploy.local), name = "address-receiver")
        enterBarrier("second-address-receiver-ready")
      }

      runOn(second) {
        enterBarrier("second-address-receiver-ready")
        secondUniqueAddress = Cluster(secondSystem).selfUniqueAddress
        List(first, third).foreach { r =>
          system.actorSelection(RootActorPath(r) / "user" / "address-receiver") ! secondUniqueAddress
          expectMsg(5.seconds, "ok")
        }
      }
      enterBarrier("second-address-transferred")

      // now we can join first, secondSystem, third together
      runOn(first, third) {
        cluster.joinSeedNodes(seedNodes)
        awaitMembersUp(3)
      }
      runOn(second) {
        Cluster(secondSystem).joinSeedNodes(seedNodes)
        awaitAssert(Cluster(secondSystem).readView.members.size should ===(3))
        awaitAssert(Cluster(secondSystem).readView.members.unsorted.map(_.status) should ===(Set(Up)))
      }
      enterBarrier("started")

      // shutdown secondSystem
      runOn(second) {
        // send system message just before shutdown, reproducer for issue #19780
        secondSystem.actorOf(Props(classOf[Watcher], address(first), testActor), "testwatcher")
        expectMsg(Done)

        shutdown(secondSystem, remaining)
      }
      enterBarrier("second-shutdown")

      // then immediately start restartedSecondSystem, which has the same address as secondSystem
      runOn(second) {
        Cluster(restartedSecondSystem).joinSeedNodes(seedNodes)
        awaitAssert(Cluster(restartedSecondSystem).readView.members.size should ===(3))
        awaitAssert(Cluster(restartedSecondSystem).readView.members.unsorted.map(_.status) should ===(Set(Up)))
      }
      runOn(first, third) {
        awaitAssert {
          Cluster(system).readView.members.size should ===(3)
          Cluster(system).readView.members.exists { m =>
            m.address == secondUniqueAddress.address && m.uniqueAddress.longUid != secondUniqueAddress.longUid
          }
        }
      }
      enterBarrier("second-restarted")

    }

  }
}
