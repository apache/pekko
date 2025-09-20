/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.discovery.aggregate

import scala.annotation.nowarn
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem }
import pekko.discovery.{ Discovery, Lookup, ServiceDiscovery }
import pekko.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import pekko.testkit.TestKit

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.{ Config, ConfigFactory }

class StubbedServiceDiscovery(@nowarn("msg=never used") system: ExtendedActorSystem) extends ServiceDiscovery {

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    if (query.serviceName == "stubbed") {
      Future.successful(
        Resolved(
          query.serviceName,
          immutable.Seq(ResolvedTarget(host = "stubbed1", port = Some(1234), address = None))))
    } else if (query.serviceName == "fail") {
      Future.failed(new RuntimeException("No resolving for you!"))
    } else {
      Future.successful(Resolved(query.serviceName, immutable.Seq.empty))
    }
  }
}

object AggregateServiceDiscoverySpec {
  val config: Config = ConfigFactory.parseString("""
      pekko {
        loglevel = DEBUG
        discovery {
          method = aggregate

          aggregate {
            discovery-methods = ["stubbed1", "config"]
          }
        }
      }

      pekko.discovery.stubbed1 {
        class = org.apache.pekko.discovery.aggregate.StubbedServiceDiscovery
      }

      pekko.discovery.config.services = {
        config1 = {
          endpoints = [
            {
              host = "cat"
              port = 1233
            },
            {
              host = "dog"
              port = 1234
            }
          ]
        },
        fail = {
          endpoints = [
            {
              host = "from-config"
            }
          ]
        }
      }
    """)
}

class AggregateServiceDiscoverySpec
    extends TestKit(ActorSystem("AggregateDiscoverySpec", AggregateServiceDiscoverySpec.config))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val discovery: ServiceDiscovery = Discovery(system).discovery

  "Aggregate service discovery" must {

    "only call first one if returns results" in {
      val results = discovery.lookup("stubbed", 100.millis).futureValue
      results shouldEqual Resolved(
        "stubbed",
        immutable.Seq(ResolvedTarget(host = "stubbed1", port = Some(1234), address = None)))
    }

    "move onto the next if no resolved targets" in {
      val results = discovery.lookup("config1", 100.millis).futureValue
      results shouldEqual Resolved(
        "config1",
        immutable.Seq(
          ResolvedTarget(host = "cat", port = Some(1233), address = None),
          ResolvedTarget(host = "dog", port = Some(1234), address = None)))
    }

    "move onto next if fails" in {
      val results = discovery.lookup("fail", 100.millis).futureValue
      // Stub fails then result comes from config
      results shouldEqual Resolved(
        "fail",
        immutable.Seq(ResolvedTarget(host = "from-config", port = None, address = None)))
    }
  }

}
