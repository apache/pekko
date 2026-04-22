/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

/**
 * Specifies timeouts for the TCK
 */
object Timeouts {

  // Scale TCK timeouts by pekko.test.timefactor.
  private val timeFactor: Double =
    sys.props.get("pekko.test.timefactor").map(_.toDouble).getOrElse(1.0)

  def publisherShutdownTimeoutMillis: Int = 3000

  def defaultTimeoutMillis: Int = (800 * timeFactor).toInt

  def defaultNoSignalsTimeoutMillis: Int = (200 * timeFactor).toInt

  def actorSystemShutdownTimeoutMillis: Int = math.ceil(10000 * timeFactor).toInt

}
