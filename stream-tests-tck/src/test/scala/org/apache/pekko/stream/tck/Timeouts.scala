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

  def publisherShutdownTimeoutMillis: Int = math.ceil(3000 * timeFactor).toInt

  def defaultTimeoutMillis: Int = (800 * timeFactor).toInt

  def defaultNoSignalsTimeoutMillis: Int = (200 * timeFactor).toInt

  def actorSystemShutdownTimeoutMillis: Int = math.ceil(10000 * timeFactor).toInt

  // Used by TCK Test classes that need to materialize an intermediate stream to
  // obtain the Publisher under test (e.g. groupBy, prefixAndTail). The base 3s
  // is the historical hardcoded value; scaling by timeFactor keeps it stable on
  // JDK 25 nightly runs where compounded GC + ForkJoinPool pressure occasionally
  // pushes a single materialization beyond 3s during 100-iteration stochastic
  // tests, which previously caused the TCK suite to abort with "signalled 0".
  def materializerTimeoutMillis: Int = math.ceil(3000 * timeFactor).toInt

}
