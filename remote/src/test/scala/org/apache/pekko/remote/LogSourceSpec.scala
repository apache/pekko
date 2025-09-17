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

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, Deploy, ExtendedActorSystem, Props }
import pekko.event.Logging
import pekko.event.Logging.Info
import pekko.testkit.{ PekkoSpec, TestProbe }

object LogSourceSpec {
  class Reporter extends Actor with ActorLogging {
    def receive = {
      case s: String =>
        log.info(s)
    }
  }
}

class LogSourceSpec extends PekkoSpec("""
    pekko.loglevel = INFO
    pekko.actor.provider = remote
    pekko.remote.classic.netty.tcp.port = 0
  """) {

  import LogSourceSpec._

  val reporter = system.actorOf(Props[Reporter](), "reporter")
  val logProbe = TestProbe()
  system.eventStream.subscribe(system.actorOf(Props(new Actor {
      def receive = {
        case i @ Info(_, _, msg: String) if msg contains "hello" => logProbe.ref ! i
        case _                                                   =>
      }
    }).withDeploy(Deploy.local), "logSniffer"), classOf[Logging.Info])

  "Log events" must {

    "should include host and port for local LogSource" in {
      reporter ! "hello"
      val info = logProbe.expectMsgType[Info]
      info.message should ===("hello")
      val defaultAddress = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress
      info.logSource should include(defaultAddress.toString)
    }
  }
}
