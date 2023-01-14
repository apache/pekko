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

package org.apache.pekko.event.slf4j

import scala.concurrent.duration._

import org.scalatest.BeforeAndAfterEach

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, Props }
import pekko.actor.ActorRef
import pekko.event.Logging
import pekko.event.Logging.Debug
import pekko.event.Logging.Info
import pekko.event.Logging.InitializeLogger
import pekko.event.Logging.LogEvent
import pekko.event.Logging.LoggerInitialized
import pekko.event.Logging.Warning
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe

object Slf4jLoggingFilterSpec {

  // This test depends on logback configuration in src/test/resources/logback-test.xml

  val config = """
    pekko {
      loglevel = DEBUG # test verifies debug
      loggers = ["org.apache.pekko.event.slf4j.Slf4jLoggingFilterSpec$TestLogger"]
      logging-filter = "org.apache.pekko.event.slf4j.Slf4jLoggingFilter"
    }
    """

  final case class SetTarget(ref: ActorRef)

  class TestLogger extends Actor {
    var target: Option[ActorRef] = None
    override def receive: Receive = {
      case InitializeLogger(bus) =>
        bus.subscribe(context.self, classOf[SetTarget])
        sender() ! LoggerInitialized
      case SetTarget(ref) =>
        target = Some(ref)
        ref ! "OK"
      case event: LogEvent =>
        println("# event: " + event)
        target.foreach { _ ! event }
    }
  }

  class DebugLevelProducer extends Actor with ActorLogging {
    def receive = {
      case s: String =>
        log.warning(s)
        log.info(s)
        println("# DebugLevelProducer: " + log.isDebugEnabled)
        log.debug(s)
    }
  }

  class WarningLevelProducer extends Actor with ActorLogging {
    def receive = {
      case s: String =>
        log.warning(s)
        log.info(s)
        log.debug(s)
    }
  }

}

class Slf4jLoggingFilterSpec extends PekkoSpec(Slf4jLoggingFilterSpec.config) with BeforeAndAfterEach {
  import Slf4jLoggingFilterSpec._

  "Slf4jLoggingFilter" must {

    "use configured LoggingFilter at debug log level in logback conf" in {
      val log1 = Logging(system, classOf[DebugLevelProducer])
      log1.isDebugEnabled should be(true)
      log1.isInfoEnabled should be(true)
      log1.isWarningEnabled should be(true)
      log1.isErrorEnabled should be(true)
    }

    "use configured LoggingFilter at warning log level in logback conf" in {
      val log1 = Logging(system, classOf[WarningLevelProducer])
      log1.isDebugEnabled should be(false)
      log1.isInfoEnabled should be(false)
      log1.isWarningEnabled should be(true)
      log1.isErrorEnabled should be(true)
    }

    "filter ActorLogging at debug log level with logback conf" in {
      val probe = TestProbe()
      system.eventStream.publish(SetTarget(probe.ref))
      probe.expectMsg("OK")
      val debugLevelProducer = system.actorOf(Props[DebugLevelProducer](), name = "debugLevelProducer")
      debugLevelProducer ! "test1"
      probe.expectMsgType[Warning].message should be("test1")
      probe.expectMsgType[Info].message should be("test1")
      probe.expectMsgType[Debug].message should be("test1")
    }

    "filter ActorLogging at warning log level with logback conf" in {
      val probe = TestProbe()
      system.eventStream.publish(SetTarget(probe.ref))
      probe.expectMsg("OK")
      val debugLevelProducer = system.actorOf(Props[WarningLevelProducer](), name = "warningLevelProducer")
      debugLevelProducer ! "test2"
      probe.expectMsgType[Warning].message should be("test2")
      probe.expectNoMessage(500.millis)
    }
  }

}
