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

package org.apache.pekko.discovery

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.discovery.ServiceDiscovery.Resolved
import pekko.testkit.TestKit

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.typesafe.config.ConfigFactory

class DiscoveryConfigurationSpec extends AnyWordSpec with Matchers {

  "ServiceDiscovery" should {
    "throw when no default discovery configured" in {
      val sys = ActorSystem("DiscoveryConfigurationSpec")
      try {
        val ex = intercept[Exception] {
          Discovery(sys).discovery
        }
        ex.getMessage should include("No default service discovery implementation configured")
      } finally TestKit.shutdownActorSystem(sys)
    }

    "select implementation from config by config name (inside pekko.discovery namespace)" in {
      val className = classOf[FakeTestDiscovery].getCanonicalName

      val sys = ActorSystem(
        "DiscoveryConfigurationSpec",
        ConfigFactory.parseString(s"""
            pekko.discovery {
              method = pekko-mock-inside

              pekko-mock-inside {
                class = $className
              }
            }
        """).withFallback(ConfigFactory.load()))

      try Discovery(sys).discovery.getClass.getCanonicalName should ===(className)
      finally TestKit.shutdownActorSystem(sys)
    }

    "load another implementation from config by config name" in {
      val className1 = classOf[FakeTestDiscovery].getCanonicalName
      val className2 = classOf[FakeTestDiscovery2].getCanonicalName

      val sys = ActorSystem(
        "DiscoveryConfigurationSpec",
        ConfigFactory.parseString(s"""
            pekko.discovery {
              method = mock1

              mock1 {
                class = $className1
              }
              mock2 {
                class = $className2
              }
            }
        """).withFallback(ConfigFactory.load()))

      try {
        Discovery(sys).discovery.getClass.getCanonicalName should ===(className1)
        Discovery(sys).loadServiceDiscovery("mock2").getClass.getCanonicalName should ===(className2)
      } finally TestKit.shutdownActorSystem(sys)
    }

    "return same instance for same method" in {
      val className1 = classOf[FakeTestDiscovery].getCanonicalName
      val className2 = classOf[FakeTestDiscovery2].getCanonicalName

      val sys = ActorSystem(
        "DiscoveryConfigurationSpec",
        ConfigFactory.parseString(s"""
            pekko.discovery {
              method = mock1

              mock1 {
                class = $className1
              }
              mock2 {
                class = $className2
              }
            }
        """).withFallback(ConfigFactory.load()))

      try {
        (Discovery(sys).loadServiceDiscovery("mock2") should be)
          .theSameInstanceAs(Discovery(sys).loadServiceDiscovery("mock2"))

        (Discovery(sys).discovery should be).theSameInstanceAs(Discovery(sys).loadServiceDiscovery("mock1"))
      } finally TestKit.shutdownActorSystem(sys)
    }

    "throw a specific discovery method exception" in {
      val className = classOf[ExceptionThrowingDiscovery].getCanonicalName

      val sys = ActorSystem(
        "DiscoveryConfigurationSpec",
        ConfigFactory.parseString(s"""
            pekko.discovery {
              method = "mock1"
               mock1 {
                class = $className
              }
            }
        """).withFallback(ConfigFactory.load()))

      try {
        an[DiscoveryException] should be thrownBy Discovery(sys).discovery
      } finally TestKit.shutdownActorSystem(sys)
    }

    "throw an illegal argument exception for not existing method" in {
      val className = "className"

      val sys = ActorSystem(
        "DiscoveryConfigurationSpec",
        ConfigFactory.parseString(s"""
            pekko.discovery {
              method = "$className"
            }
        """).withFallback(ConfigFactory.load()))

      try {
        an[IllegalArgumentException] should be thrownBy Discovery(sys).discovery
      } finally TestKit.shutdownActorSystem(sys)
    }

  }

}

class FakeTestDiscovery extends ServiceDiscovery {

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = ???
}

class FakeTestDiscovery2 extends FakeTestDiscovery

class DiscoveryException(message: String) extends Exception(message)

class ExceptionThrowingDiscovery extends FakeTestDiscovery {
  bad()
  def bad(): Unit = {
    throw new DiscoveryException("oh no")
  }
}
