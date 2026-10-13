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

package org.apache.pekko.cluster.routing

import org.apache.pekko
import pekko.actor._
import pekko.actor.OneForOneStrategy
import pekko.cluster.Cluster
import pekko.cluster.ClusterEvent.CurrentClusterState
import pekko.cluster.MemberStatus
import pekko.routing.AdjustPoolSize
import pekko.routing.GetRoutees
import pekko.routing.RoundRobinPool
import pekko.routing.Routees
import pekko.testkit._

object ClusterRouterSupervisorSpec {

  class KillableActor() extends Actor {

    def receive = {
      case "go away" =>
        throw new IllegalArgumentException("Goodbye then!")
    }

  }

}

class ClusterRouterSupervisorSpec extends PekkoSpec("""
  pekko.actor.provider = "cluster"
  pekko.remote.classic.netty.tcp.port = 0
  pekko.remote.artery.canonical.port = 0
""") {

  import ClusterRouterSupervisorSpec._

  private val cluster = Cluster(system)

  override protected def atStartup(): Unit = {
    cluster.join(cluster.selfAddress)
    awaitAssert(cluster.selfMember.status should ===(MemberStatus.Up))
  }

  private def routeesAfterInitialClusterState(router: ActorRef): Routees = {
    // Ensure preStart has sent the subscription before asking the cluster event publisher for a barrier.
    router.tell(GetRoutees, testActor)
    expectMsgType[Routees]
    cluster.sendCurrentClusterState(testActor)
    expectMsgType[CurrentClusterState]

    // The subscription snapshot was enqueued before the barrier reply, so this query observes it.
    router.tell(GetRoutees, testActor)
    expectMsgType[Routees]
  }

  "Cluster aware routers" must {

    "use provided supervisor strategy" in {
      val router = system.actorOf(
        ClusterRouterPool(
          RoundRobinPool(nrOfInstances = 1,
            supervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
              case _ =>
                testActor ! "supervised"
                SupervisorStrategy.Stop
            }),
          ClusterRouterPoolSettings(totalInstances = 1, maxInstancesPerNode = 1, allowLocalRoutees = true))
          .props(Props(classOf[KillableActor])),
        name = "therouter")

      router ! "go away"
      expectMsg("supervised")
    }

    "not create local routees through AdjustPoolSize when local routees are disabled" in {
      val router = system.actorOf(
        ClusterRouterPool(
          RoundRobinPool(nrOfInstances = 0),
          ClusterRouterPoolSettings(totalInstances = 1, maxInstancesPerNode = 1, allowLocalRoutees = false))
          .props(Props(classOf[KillableActor])))

      router ! AdjustPoolSize(1)
      router.tell(GetRoutees, testActor)

      expectMsg(Routees(Vector.empty))
    }

    "not create local routees through AdjustPoolSize when local roles do not match" in {
      val router = system.actorOf(
        ClusterRouterPool(
          RoundRobinPool(nrOfInstances = 0),
          ClusterRouterPoolSettings(
            totalInstances = 1,
            maxInstancesPerNode = 1,
            allowLocalRoutees = true,
            useRoles = Set("worker")))
          .props(Props(classOf[KillableActor])))

      router ! AdjustPoolSize(1)
      router.tell(GetRoutees, testActor)

      expectMsg(Routees(Vector.empty))
    }

    "not exceed the per-node limit through AdjustPoolSize" in {
      val router = system.actorOf(
        ClusterRouterPool(
          RoundRobinPool(nrOfInstances = 0),
          ClusterRouterPoolSettings(totalInstances = 2, maxInstancesPerNode = 1, allowLocalRoutees = true))
          .props(Props(classOf[KillableActor])))

      router ! AdjustPoolSize(1)
      router ! AdjustPoolSize(1)
      router.tell(GetRoutees, testActor)

      expectMsgType[Routees].routees.size should ===(1)
    }

    "not exceed the total routee limit through AdjustPoolSize" in {
      val router = system.actorOf(
        ClusterRouterPool(
          RoundRobinPool(nrOfInstances = 0),
          ClusterRouterPoolSettings(totalInstances = 3, maxInstancesPerNode = 3, allowLocalRoutees = true))
          .props(Props(classOf[KillableActor])))

      routeesAfterInitialClusterState(router).routees.size should ===(3)

      router ! AdjustPoolSize(-2)
      router.tell(GetRoutees, testActor)
      expectMsgType[Routees].routees.size should ===(1)

      router ! AdjustPoolSize(5)
      router.tell(GetRoutees, testActor)
      expectMsgType[Routees].routees.size should ===(3)
    }

    "add only the requested number of routees through AdjustPoolSize" in {
      val router = system.actorOf(
        ClusterRouterPool(
          RoundRobinPool(nrOfInstances = 0),
          ClusterRouterPoolSettings(totalInstances = 3, maxInstancesPerNode = 3, allowLocalRoutees = true))
          .props(Props(classOf[KillableActor])))

      routeesAfterInitialClusterState(router).routees.size should ===(3)

      router ! AdjustPoolSize(-2)
      router.tell(GetRoutees, testActor)
      expectMsgType[Routees].routees.size should ===(1)

      router ! AdjustPoolSize(1)
      router.tell(GetRoutees, testActor)

      expectMsgType[Routees].routees.size should ===(2)
    }

  }

}
