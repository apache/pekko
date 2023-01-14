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

package org.apache.pekko.cluster.routing

import org.apache.pekko
import pekko.actor._
import pekko.actor.OneForOneStrategy
import pekko.routing.RoundRobinPool
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

  }

}
