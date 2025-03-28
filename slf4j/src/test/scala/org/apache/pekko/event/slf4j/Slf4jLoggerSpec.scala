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

package org.apache.pekko.event.slf4j

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

import scala.concurrent.duration._

import ch.qos.logback.core.OutputStreamAppender
import org.scalatest.BeforeAndAfterEach
import org.slf4j.{ Marker, MarkerFactory }

import org.apache.pekko
import pekko.actor.{ Actor, DiagnosticActorLogging, Props }
import pekko.event.{ LogMarker, Logging }
import pekko.testkit.PekkoSpec

object Slf4jLoggerSpec {

  // This test depends on logback configuration in src/test/resources/logback-test.xml

  val config = """
    pekko {
      loglevel = INFO
      loggers = ["org.apache.pekko.event.slf4j.Slf4jLogger"]
      logger-startup-timeout = 30s
    }
    """

  final case class StringWithMDC(s: String, mdc: Map[String, Any])
  final case class StringWithMarker(s: String, marker: LogMarker)
  final case class StringWithSlf4jMarker(s: String, marker: Marker)
  final case class StringWithSlf4jMarkerMDC(s: String, marker: Marker, mdc: Map[String, Any])

  final class LogProducer extends Actor with DiagnosticActorLogging {

    val markLog = Logging.withMarker(this)

    def receive = {
      case e: Exception =>
        log.error(e, e.getMessage)
      case (s: String, x: Int, y: Int) =>
        log.info(s, x, y)
      case StringWithSlf4jMarker(s, m) =>
        markLog.info(Slf4jLogMarker(m), s)
      case StringWithSlf4jMarkerMDC(s, mark, mdc) =>
        markLog.mdc(mdc)
        markLog.info(Slf4jLogMarker(mark), s)
        markLog.clearMDC()
      case StringWithMDC(s, mdc) =>
        log.mdc(mdc)
        log.info(s)
        log.clearMDC()
      case StringWithMarker(s, marker) =>
        markLog.info(marker, s)
    }
  }

  class MyLogSource

  val output = new ByteArrayOutputStream
  def outputString: String = output.toString(StandardCharsets.UTF_8.name)

  class TestAppender[E] extends OutputStreamAppender[E] {

    override def start(): Unit = {
      setOutputStream(output)
      super.start()
    }
  }

}

class Slf4jLoggerSpec extends PekkoSpec(Slf4jLoggerSpec.config) with BeforeAndAfterEach {
  import Slf4jLoggerSpec._

  val producer = system.actorOf(Props[LogProducer](), name = "logProducer")

  override def beforeEach(): Unit = {
    output.reset()
  }

  val sourceThreadRegex = "sourceThread=Slf4jLoggerSpec-pekko.actor.default-dispatcher-[1-9][0-9]*"

  "Slf4jLogger" must {

    "log error with stackTrace" in {
      producer ! new RuntimeException("Simulated error")

      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("pekkoSource=pekko://Slf4jLoggerSpec/user/logProducer")
      s should include("pekkoAddress=pekko://Slf4jLoggerSpec")
      s should include("pekkoUid=")
      s should include("level=[ERROR]")
      s should include("logger=[org.apache.pekko.event.slf4j.Slf4jLoggerSpec$LogProducer]")
      (s should include).regex(sourceThreadRegex)
      s should include("msg=[Simulated error]")
      s should include("java.lang.RuntimeException: Simulated error")
      s should include("at org.apache.pekko.event.slf4j.Slf4jLoggerSpec")
    }

    "log info with parameters" in {
      producer ! (("test x={} y={}", 3, 17))

      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("pekkoSource=pekko://Slf4jLoggerSpec/user/logProducer")
      s should include("pekkoAddress=pekko://Slf4jLoggerSpec")
      s should include("level=[INFO]")
      s should include("logger=[org.apache.pekko.event.slf4j.Slf4jLoggerSpec$LogProducer]")
      (s should include).regex(sourceThreadRegex)
      s should include("msg=[test x=3 y=17]")
    }

    "log info with marker" in {
      producer ! StringWithMarker("security-wise interesting message", LogMarker("SECURITY"))

      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("marker=[SECURITY]")
      s should include("msg=[security-wise interesting message]")
    }

    "log info with marker and properties" in {
      producer ! StringWithMarker("interesting message", LogMarker("testMarker", Map("p1" -> 1, "p2" -> "B")))

      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("marker=[testMarker]")
      s should include("p1=1")
      s should include("p2=B")
      s should include("msg=[interesting message]")
    }

    "log info with slf4j marker" in {
      val slf4jMarker = MarkerFactory.getMarker("SLF")
      slf4jMarker.add(MarkerFactory.getMarker("ADDED")) // slf4j markers can have children
      producer ! StringWithSlf4jMarker("security-wise interesting message", slf4jMarker)

      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("marker=[SLF [ ADDED ]]")
      s should include("msg=[security-wise interesting message]")
    }
    "log info with slf4j marker and MDC" in {
      val slf4jMarker = MarkerFactory.getMarker("SLF")
      slf4jMarker.add(MarkerFactory.getMarker("ADDED")) // slf4j markers can have children
      producer ! StringWithSlf4jMarkerMDC(
        "security-wise interesting message",
        slf4jMarker,
        Map("ticketNumber" -> 3671, "ticketDesc" -> "Custom MDC Values"))

      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("marker=[SLF [ ADDED ]]")
      s should include("ticketDesc=Custom MDC Values")
      s should include("msg=[security-wise interesting message]")
    }

    "put custom MDC values when specified" in {
      producer ! StringWithMDC(
        "Message with custom MDC values",
        Map("ticketNumber" -> 3671, "ticketDesc" -> "Custom MDC Values"))

      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("pekkoSource=pekko://Slf4jLoggerSpec/user/logProducer")
      s should include("level=[INFO]")
      s should include("logger=[org.apache.pekko.event.slf4j.Slf4jLoggerSpec$LogProducer]")
      (s should include).regex(sourceThreadRegex)
      s should include("ticketDesc=Custom MDC Values")
      s should include("msg=[Message with custom MDC values]")
    }

    "support null marker" in {
      producer ! StringWithMarker("security-wise interesting message", null)

      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("msg=[security-wise interesting message]")
    }

    "Support null values in custom MDC" in {
      producer ! StringWithMDC("Message with null custom MDC values", Map("ticketNumber" -> 3671, "ticketDesc" -> null))

      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("pekkoSource=pekko://Slf4jLoggerSpec/user/logProducer")
      s should include("level=[INFO]")
      s should include("logger=[org.apache.pekko.event.slf4j.Slf4jLoggerSpec$LogProducer]")
      println(s)
      (s should include).regex(sourceThreadRegex)
      s should include("ticketDesc=null")
      s should include("msg=[Message with null custom MDC values]")
    }

    "include system info in pekkoSource when creating Logging with system" in {
      val log = Logging(system, "org.apache.pekko.event.slf4j.Slf4jLoggerSpec.MyLogSource")
      log.info("test")
      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("pekkoSource=org.apache.pekko.event.slf4j.Slf4jLoggerSpec.MyLogSource(pekko://Slf4jLoggerSpec)")
      s should include("logger=[org.apache.pekko.event.slf4j.Slf4jLoggerSpec.MyLogSource(pekko://Slf4jLoggerSpec)]")
    }

    "not include system info in pekkoSource when creating Logging with system.eventStream" in {
      val log = Logging(system.eventStream, "org.apache.pekko.event.slf4j.Slf4jLoggerSpec.MyLogSource")
      log.info("test")
      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("pekkoSource=org.apache.pekko.event.slf4j.Slf4jLoggerSpec.MyLogSource")
      s should include("logger=[org.apache.pekko.event.slf4j.Slf4jLoggerSpec.MyLogSource]")
    }

    "use short class name and include system info in pekkoSource when creating Logging with system and class" in {
      val log = Logging(system, classOf[MyLogSource])
      log.info("test")
      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("pekkoSource=Slf4jLoggerSpec$MyLogSource(pekko://Slf4jLoggerSpec)")
      s should include("logger=[org.apache.pekko.event.slf4j.Slf4jLoggerSpec$MyLogSource]")
    }

    "use short class name in pekkoSource when creating Logging with system.eventStream and class" in {
      val log = Logging(system.eventStream, classOf[MyLogSource])
      log.info("test")
      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("pekkoSource=Slf4jLoggerSpec$MyLogSource")
      s should include("logger=[org.apache.pekko.event.slf4j.Slf4jLoggerSpec$MyLogSource]")
    }

    "include actorSystem name in sourceActorSystem" in {
      val log = Logging(system.eventStream, classOf[MyLogSource])
      log.info("test")
      awaitCond(outputString.contains("----"), 5.seconds)
      val s = outputString
      s should include("sourceActorSystem=Slf4jLoggerSpec")
    }
  }

}
