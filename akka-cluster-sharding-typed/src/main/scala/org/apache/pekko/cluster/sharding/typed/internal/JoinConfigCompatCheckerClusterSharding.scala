/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.internal

import scala.collection.{ immutable => im }

import com.typesafe.config.Config

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.cluster.{ ConfigValidation, JoinConfigCompatChecker, Valid }

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class JoinConfigCompatCheckerClusterSharding extends JoinConfigCompatChecker {

  override def requiredKeys: im.Seq[String] =
    im.Seq("pekko.cluster.sharding.number-of-shards")

  override def check(toCheck: Config, actualConfig: Config): ConfigValidation = {
    if (toCheck.hasPath(requiredKeys.head))
      JoinConfigCompatChecker.fullMatch(requiredKeys, toCheck, actualConfig)
    else
      Valid // support for rolling update, property doesn't exist in previous versions
  }
}
