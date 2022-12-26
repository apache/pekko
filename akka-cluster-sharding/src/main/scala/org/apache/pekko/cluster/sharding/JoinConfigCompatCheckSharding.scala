/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import scala.collection.{ immutable => im }

import com.typesafe.config.Config

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.cluster.{ ConfigValidation, JoinConfigCompatChecker }

/**
 * INTERNAL API
 */
@InternalApi
final class JoinConfigCompatCheckSharding extends JoinConfigCompatChecker {

  override def requiredKeys: im.Seq[String] =
    im.Seq("pekko.cluster.sharding.state-store-mode")

  override def check(toCheck: Config, actualConfig: Config): ConfigValidation =
    JoinConfigCompatChecker.fullMatch(requiredKeys, toCheck, actualConfig)
}
