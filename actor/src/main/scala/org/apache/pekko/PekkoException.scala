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

package org.apache.pekko

/**
 * Pekko base Exception.
 */
@SerialVersionUID(1L)
class PekkoException(message: String, cause: Throwable) extends RuntimeException(message, cause) with Serializable {
  def this(msg: String) = this(msg, null)
}

/**
 * Mix in this trait to suppress the StackTrace for the instance of the exception but not the cause,
 * scala.util.control.NoStackTrace suppresses all the StackTraces.
 */
trait OnlyCauseStackTrace { self: Throwable =>
  override def fillInStackTrace(): Throwable = {
    setStackTrace(getCause match {
      case null => Array.empty
      case some => some.getStackTrace
    })
    this
  }
}

/**
 * This exception is thrown when Apache Pekko detects a problem with the provided configuration
 */
class ConfigurationException(message: String, cause: Throwable) extends PekkoException(message, cause) {
  def this(msg: String) = this(msg, null)
}
