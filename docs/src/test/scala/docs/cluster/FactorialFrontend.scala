/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package scala.docs.cluster

import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.apache.pekko.cluster.Cluster
import org.apache.pekko.routing.FromConfig
import org.apache.pekko.actor.ReceiveTimeout
import scala.util.Try
import scala.concurrent.Await

//#frontend
class FactorialFrontend(upToN: Int, repeat: Boolean) extends Actor with ActorLogging {

  val backend = context.actorOf(FromConfig.props(), name = "factorialBackendRouter")

  override def preStart(): Unit = {
    sendJobs()
    if (repeat) {
      context.setReceiveTimeout(10.seconds)
    }
  }

  def receive = {
    case (n: Int, factorial: BigInt) =>
      if (n == upToN) {
        log.debug("{}! = {}", n, factorial)
        if (repeat) sendJobs()
        else context.stop(self)
      }
    case ReceiveTimeout =>
      log.info("Timeout")
      sendJobs()
  }

  def sendJobs(): Unit = {
    log.info("Starting batch of factorials up to [{}]", upToN)
    (1 to upToN).foreach(backend ! _)
  }
}
//#frontend

object FactorialFrontend {
  def main(args: Array[String]): Unit = {
    val upToN = 200

    val config =
      ConfigFactory.parseString("pekko.cluster.roles = [frontend]").withFallback(ConfigFactory.load("factorial"))

    val system = ActorSystem("ClusterSystem", config)
    system.log.info("Factorials will start when 2 backend members in the cluster.")
    // #registerOnUp
    Cluster(system).registerOnMemberUp {
      system.actorOf(Props(classOf[FactorialFrontend], upToN, true), name = "factorialFrontend")
    }
    // #registerOnUp

  }
}

// not used, only for documentation
abstract class FactorialFrontend2 extends Actor {
  // #router-lookup-in-code
  import org.apache.pekko
  import pekko.cluster.routing.ClusterRouterGroup
  import pekko.cluster.routing.ClusterRouterGroupSettings
  import pekko.cluster.metrics.AdaptiveLoadBalancingGroup
  import pekko.cluster.metrics.HeapMetricsSelector

  val backend = context.actorOf(
    ClusterRouterGroup(
      AdaptiveLoadBalancingGroup(HeapMetricsSelector),
      ClusterRouterGroupSettings(
        totalInstances = 100,
        routeesPaths = List("/user/factorialBackend"),
        allowLocalRoutees = true,
        useRoles = Set("backend"))).props(),
    name = "factorialBackendRouter2")

  // #router-lookup-in-code
}

// not used, only for documentation
abstract class FactorialFrontend3 extends Actor {
  import org.apache.pekko
  // #router-deploy-in-code
  import pekko.cluster.routing.ClusterRouterPool
  import pekko.cluster.routing.ClusterRouterPoolSettings
  import pekko.cluster.metrics.AdaptiveLoadBalancingPool
  import pekko.cluster.metrics.SystemLoadAverageMetricsSelector

  val backend = context.actorOf(
    ClusterRouterPool(
      AdaptiveLoadBalancingPool(SystemLoadAverageMetricsSelector),
      ClusterRouterPoolSettings(
        totalInstances = 100,
        maxInstancesPerNode = 3,
        allowLocalRoutees = false,
        useRoles = Set("backend"))).props(Props[FactorialBackend]()),
    name = "factorialBackendRouter3")
  // #router-deploy-in-code
}
