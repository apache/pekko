/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery.tcp

import java.security.SecureRandom

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.event.MarkerLoggingAdapter
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object SecureRandomFactory {

  val GeneratorJdkSecureRandom = "SecureRandom"

  /**
   * INTERNAL API
   */
  @InternalApi
  // extracted as a method for testing
  private[tcp] def rngConfig(config: Config) = {
    config.getString("random-number-generator")
  }

  def createSecureRandom(config: Config, log: MarkerLoggingAdapter): SecureRandom = {
    createSecureRandom(rngConfig(config), log)
  }

  def createSecureRandom(randomNumberGenerator: String, log: MarkerLoggingAdapter): SecureRandom = {
    val rng = randomNumberGenerator match {
      case "" | GeneratorJdkSecureRandom =>
        log.debug("Using platform default SecureRandom algorithm for SSL")
        new SecureRandom
      case custom =>
        log.debug("Using {} SecureRandom algorithm for SSL", custom)
        SecureRandom.getInstance(custom)
    }
    rng.nextInt() // prevent stall on first access
    rng
  }
}
