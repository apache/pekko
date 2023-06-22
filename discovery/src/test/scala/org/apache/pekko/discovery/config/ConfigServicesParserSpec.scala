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

package org.apache.pekko.discovery.config

import scala.collection.immutable

import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import pekko.discovery.config.ConfigServicesParserSpec._

object ConfigServicesParserSpec {
  val exampleConfig: Config = ConfigFactory.parseString("""
      services {
        service1 {
          endpoints = [
            {
              host = "cat"
              port = 1233
            },
            {
              host = "dog"
            }
          ]
        },
        service2 {
          endpoints = []
        }
      }
    """.stripMargin)
}

class ConfigServicesParserSpec extends AnyWordSpec with Matchers {

  "Config parsing" must {
    "parse" in {
      val config = exampleConfig.getConfig("services")

      val result = ConfigServicesParser.parse(config)

      result("service1") shouldEqual Resolved(
        "service1",
        immutable.Seq(
          ResolvedTarget(host = "cat", port = Some(1233), address = None),
          ResolvedTarget(host = "dog", port = None, address = None)))
      result("service2") shouldEqual Resolved("service2", immutable.Seq())
    }
  }
}
