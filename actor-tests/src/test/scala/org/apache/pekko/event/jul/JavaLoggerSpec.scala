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

package org.apache.pekko.event.jul

import java.util.logging

import scala.util.control.NoStackTrace

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, Props }
import pekko.testkit.PekkoSpec

@deprecated("Use SLF4J instead.", "Akka 2.6.0")
object JavaLoggerSpec {

  val config = ConfigFactory.parseString("""
    pekko {
      loglevel = INFO
      loggers = ["org.apache.pekko.event.jul.JavaLogger"]
      logging-filter = "org.apache.pekko.event.jul.JavaLoggingFilter"
    }""")

  class LogProducer extends Actor with ActorLogging {
    def receive = {
      case e: Exception =>
        log.error(e, e.getMessage)
      case (s: String, x: Int) =>
        log.info(s, x)
    }
  }

  class SimulatedExc extends RuntimeException("Simulated error") with NoStackTrace
}

@deprecated("Use SLF4J instead.", "Akka 2.6.0")
class JavaLoggerSpec extends PekkoSpec(JavaLoggerSpec.config) {

  val logger = logging.Logger.getLogger(classOf[JavaLoggerSpec.LogProducer].getName)
  logger.setUseParentHandlers(false) // turn off output of test LogRecords
  logger.addHandler(new logging.Handler {
    def publish(record: logging.LogRecord): Unit =
      testActor ! record

    def flush(): Unit = {}
    def close(): Unit = {}
  })

  val producer = system.actorOf(Props[JavaLoggerSpec.LogProducer](), name = "log")

  "JavaLogger" must {

    "log error with stackTrace" in {
      producer ! new JavaLoggerSpec.SimulatedExc

      val record = expectMsgType[logging.LogRecord]

      record should not be null
      record.getMillis should not be 0
      record.getThreadID should not be 0
      record.getLevel should ===(logging.Level.SEVERE)
      record.getMessage should ===("Simulated error")
      record.getThrown.getClass should ===(classOf[JavaLoggerSpec.SimulatedExc])
      record.getSourceClassName should ===(classOf[JavaLoggerSpec.LogProducer].getName)
      record.getSourceMethodName should ===(null)
    }

    "log info without stackTrace" in {
      producer ! (("{} is the magic number", 3))

      val record = expectMsgType[logging.LogRecord]

      record should not be null
      record.getMillis should not be 0
      record.getThreadID should not be 0
      record.getLevel should ===(logging.Level.INFO)
      record.getMessage should ===("3 is the magic number")
      record.getThrown should ===(null)
      record.getSourceClassName should ===(classOf[JavaLoggerSpec.LogProducer].getName)
      record.getSourceMethodName should ===(null)
    }
  }

}
