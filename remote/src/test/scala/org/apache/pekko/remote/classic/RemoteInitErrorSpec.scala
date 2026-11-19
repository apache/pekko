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

package org.apache.pekko.remote.classic

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.actor.ActorSystem

import org.scalatest.concurrent.Eventually._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.typesafe.config.ConfigFactory

/**
 * The 192.0.2.1 is a Documentation IP-address and should not be used at all
 * by any network node. Therefore we assume here that the initialization of
 * the ActorSystem with the use of remoting will intentionally fail.
 */
class RemoteInitErrorSpec extends AnyWordSpec with Matchers {
  val conf = ConfigFactory.parseString("""
      pekko {
        actor {
          provider = remote
        }
        remote.artery.enabled = false
        remote {
          enabled-transports = ["org.apache.pekko.remote.classic.netty.tcp"]
          classic.netty.tcp {
            hostname = "192.0.2.1"
            port = 12344
          }
        }
      }
    """).resolve()

  // All threads of an ActorSystem (dispatchers, scheduler, netty event loops) are created by its
  // thread factory and named after the system. Only look at those, so that threads of other systems
  // still shutting down in the same test JVM do not make this test fail.
  def remainingThreadNames(systemName: String): Set[String] = {
    val threads = Thread.getAllStackTraces().keySet()
    threads.asScala.collect {
      case t: Thread if !t.isDaemon() && t.getName.startsWith(systemName + "-") => t.getName
    }.toSet
  }

  "Remoting" must {
    "shut down properly on RemoteActorRefProvider initialization failure" in {
      val systemName = s"RemoteInitErrorSpec-${ThreadLocalRandom.current().nextInt(Int.MaxValue)}"
      intercept[Exception] {
        ActorSystem(systemName, ConfigFactory.parseString("pekko.loglevel=OFF").withFallback(conf))
      }
      eventually(timeout(30.seconds), interval(800.milliseconds)) {
        // no threads of the failed system should remain
        remainingThreadNames(systemName) should be(empty)
      }
    }
  }
}
