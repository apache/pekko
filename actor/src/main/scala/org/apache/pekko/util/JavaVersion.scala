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

package org.apache.pekko.util

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object JavaVersion {

  val majorVersion: Int = {
    // FIXME replace with Runtime.version() when we no longer support Java 8
    // See Oracle section 1.5.3 at:
    // https://docs.oracle.com/javase/8/docs/technotes/guides/versioning/spec/versioning2.html
    val version = System.getProperty("java.specification.version").split('.')

    val majorString =
      if (version(0) == "1") version(1) // Java 8 will be 1.8
      else version(0) // later will be 9, 10, 11 etc

    majorString.toInt
  }
}
