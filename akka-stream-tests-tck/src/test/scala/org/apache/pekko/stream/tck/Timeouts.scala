/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

/**
 * Specifies timeouts for the TCK
 */
object Timeouts {

  def publisherShutdownTimeoutMillis: Int = 3000

  def defaultTimeoutMillis: Int = 800

  def defaultNoSignalsTimeoutMillis: Int = 200

}
