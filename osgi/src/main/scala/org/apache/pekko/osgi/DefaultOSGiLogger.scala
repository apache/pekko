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

package org.apache.pekko.osgi

import org.osgi.service.log.LogService

import org.apache.pekko
import pekko.event.Logging
import pekko.event.Logging.{ DefaultLogger, LogEvent }
import pekko.event.Logging.Error.NoCause

/**
 * Logger for OSGi environment.
 * Stands for an interface between Pekko and the OSGi LogService
 * It uses the OSGi LogService to log the received LogEvents
 */
class DefaultOSGiLogger extends DefaultLogger {

  val messageFormat = " %s | %s | %s | %s"

  override def receive: Receive = uninitialisedReceive.orElse[Any, Unit](super.receive)

  /**
   * Behavior of the logger that waits for its LogService
   * @return  Receive: Store LogEvent or become initialised
   */
  def uninitialisedReceive: Receive = {
    var messagesToLog: Vector[LogEvent] = Vector()
    // the Default Logger needs to be aware of the LogService which is published on the EventStream
    context.system.eventStream.subscribe(self, classOf[LogService])
    context.system.eventStream.unsubscribe(self, UnregisteringLogService.getClass)

    /*
     * Logs every already received LogEvent and set the logger ready to log every incoming LogEvent.
     *
     * @param logService OSGi LogService that has been registered,
     */
    def setLogService(logService: LogService): Unit = {
      messagesToLog.foreach { x =>
        logMessage(logService, x)
      }
      context.become(initialisedReceive(logService))
    }

    {
      case logService: LogService => setLogService(logService)
      case logEvent: LogEvent     => messagesToLog :+= logEvent
    }
  }

  /**
   * Behavior of the Event handler that is setup (has received a LogService)
   * @param logService registered OSGi LogService
   * @return Receive : Logs LogEvent or go back to the uninitialised state
   */
  def initialisedReceive(logService: LogService): Receive = {
    context.system.eventStream.subscribe(self, UnregisteringLogService.getClass)
    context.system.eventStream.unsubscribe(self, classOf[LogService])

    {
      case logEvent: LogEvent      => logMessage(logService, logEvent)
      case UnregisteringLogService => context.become(uninitialisedReceive)
    }
  }

  /**
   * Logs a message in an OSGi LogService
   *
   * @param logService  OSGi LogService registered and used for logging
   * @param event akka LogEvent that is logged using the LogService
   */
  def logMessage(logService: LogService, event: LogEvent): Unit =
    event match {
      case error: Logging.Error if error.cause != NoCause =>
        logService.log(
          event.level.asInt,
          messageFormat.format(timestamp(event), event.thread.getName, event.logSource, event.message),
          error.cause)
      case _ =>
        logService.log(
          event.level.asInt,
          messageFormat.format(timestamp(event), event.thread.getName, event.logSource, event.message))
    }

}

/**
 * Message sent when LogService is unregistered.
 * Sent from the ActorSystemActivator to a logger (as DefaultOsgiLogger).
 */
case object UnregisteringLogService
