/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package com.typesafe.sslconfig.pekko.util

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.InternalApi
import pekko.event.{ DummyClassForStringSources, EventStream }
import pekko.event.Logging._

import com.typesafe.sslconfig.util.{ LoggerFactory, NoDepsLogger }

/**
 * Internal Usage only. Will be removed in Pekko 2.0.0.
 */
@deprecated("ssl-config support will be removed in Pekko 2.0.0. Use Tcp and TLS with SSLEngine parameters instead.",
  "1.2.1")
@InternalApi
final class PekkoLoggerFactory(system: ActorSystem) extends LoggerFactory {
  override def apply(clazz: Class[_]): NoDepsLogger = new PekkoLoggerBridge(system.eventStream, clazz)

  override def apply(name: String): NoDepsLogger =
    new PekkoLoggerBridge(system.eventStream, name, classOf[DummyClassForStringSources])
}

/**
 * Internal Usage only. Will be removed in Pekko 2.0.0.
 */
@deprecated("ssl-config support will be removed in Pekko 2.0.0. Use Tcp and TLS with SSLEngine parameters instead.",
  "1.2.1")
@InternalApi
class PekkoLoggerBridge(bus: EventStream, logSource: String, logClass: Class[_]) extends NoDepsLogger {
  def this(bus: EventStream, clazz: Class[_]) = this(bus, clazz.getCanonicalName, clazz)

  override def isDebugEnabled: Boolean = true

  override def debug(msg: String): Unit = bus.publish(Debug(logSource, logClass, msg))

  override def info(msg: String): Unit = bus.publish(Info(logSource, logClass, msg))

  override def warn(msg: String): Unit = bus.publish(Warning(logSource, logClass, msg))

  override def error(msg: String): Unit = bus.publish(Error(logSource, logClass, msg))
  override def error(msg: String, throwable: Throwable): Unit = bus.publish(Error(logSource, logClass, msg))

}
