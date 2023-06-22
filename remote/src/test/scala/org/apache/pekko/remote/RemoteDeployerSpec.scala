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

package org.apache.pekko.remote

import com.typesafe.config._

import org.apache.pekko
import pekko.ConfigurationException
import pekko.actor._
import pekko.routing._
import pekko.testkit._

object RemoteDeployerSpec {
  val deployerConf = ConfigFactory.parseString(
    """
      pekko.actor.provider = remote
      pekko.actor.deployment {
        /service2 {
          router = round-robin-pool
          nr-of-instances = 3
          remote = "pekko://sys@wallace:7355"
          dispatcher = mydispatcher
        }
      }
      pekko.remote.classic.netty.tcp.port = 0
      """,
    ConfigParseOptions.defaults)

  class RecipeActor extends Actor {
    def receive = { case _ => }
  }

}

class RemoteDeployerSpec extends PekkoSpec(RemoteDeployerSpec.deployerConf) {

  "A RemoteDeployer" must {

    "be able to parse 'pekko.actor.deployment._' with specified remote nodes" in {
      val service = "/service2"
      val deployment = system.asInstanceOf[ActorSystemImpl].provider.deployer.lookup(service.split("/").drop(1))

      deployment should ===(
        Some(
          Deploy(
            service,
            deployment.get.config,
            RoundRobinPool(3),
            RemoteScope(Address("pekko", "sys", "wallace", 7355)),
            "mydispatcher")))
    }

    "reject remote deployment when the source requires LocalScope" in {
      intercept[ConfigurationException] {
        system.actorOf(Props.empty.withDeploy(Deploy.local), "service2")
      }.getMessage should ===(
        "configuration requested remote deployment for local-only Props at [pekko://RemoteDeployerSpec/user/service2]")
    }

  }

}
