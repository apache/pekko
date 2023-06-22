/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

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
