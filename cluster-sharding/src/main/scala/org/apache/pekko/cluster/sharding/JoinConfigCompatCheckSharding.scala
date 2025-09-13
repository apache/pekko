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

package org.apache.pekko.cluster.sharding

import scala.collection.{ immutable => im }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.cluster.{ ConfigValidation, JoinConfigCompatChecker }

import com.typesafe.config.Config

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
