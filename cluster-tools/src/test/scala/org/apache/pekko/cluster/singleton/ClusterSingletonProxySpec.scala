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
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor._
import pekko.cluster.Cluster
import pekko.testkit.{ TestKit, TestProbe }

class ClusterSingletonProxySpec extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  import ClusterSingletonProxySpec._

  val seed = new ActorSys()

  val testSystems = {
    val joiners = (0 until 4).map(_ => new ActorSys(joinTo = Some(seed.cluster.selfAddress)))
    joiners :+ seed
  }

  "The cluster singleton proxy" must {
    "correctly identify the singleton" in {
      testSystems.foreach(_.testProxy("Hello"))
      testSystems.foreach(_.testProxy("World"))
    }
  }

  override def afterAll(): Unit = testSystems.foreach { sys =>
    TestKit.shutdownActorSystem(sys.system)
  }
}

object ClusterSingletonProxySpec {

  class ActorSys(name: String = "ClusterSingletonProxySystem", joinTo: Option[Address] = None)
      extends TestKit(ActorSystem(name, ConfigFactory.parseString(cfg))) {

    val cluster = Cluster(system)
    cluster.join(joinTo.getOrElse(cluster.selfAddress))

    cluster.registerOnMemberUp {
      system.actorOf(
        ClusterSingletonManager.props(
          singletonProps = Props[Singleton](),
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system).withRemovalMargin(5.seconds)),
        name = "singletonManager")
    }

    val proxy = system.actorOf(
      ClusterSingletonProxy.props("user/singletonManager", settings = ClusterSingletonProxySettings(system)),
      s"singletonProxy-${cluster.selfAddress.port.getOrElse(0)}")

    def testProxy(msg: String): Unit = {
      val probe = TestProbe()
      probe.send(proxy, msg)
      // 25 seconds to make sure the singleton was started up
      probe.expectMsg(25.seconds, s"while testing the proxy from ${cluster.selfAddress}", "Got " + msg)
    }
  }

  val cfg = """
    pekko {
      loglevel = INFO
      cluster.jmx.enabled = off
      actor.provider = "cluster"
      remote {
        classic.log-remote-lifecycle-events = off
        classic.netty.tcp {
          hostname = "127.0.0.1"
          port = 0
        }
        artery.canonical {
          hostname  = "127.0.0.1"
          port = 0
        }
      }
    }
  """

  class Singleton extends Actor with ActorLogging {

    log.info("Singleton created on {}", Cluster(context.system).selfAddress)

    def receive: Actor.Receive = {
      case msg =>
        log.info(s"Got $msg")
        sender() ! "Got " + msg
    }
  }

}
