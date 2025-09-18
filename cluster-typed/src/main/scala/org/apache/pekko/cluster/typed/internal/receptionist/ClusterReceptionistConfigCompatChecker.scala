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

package org.apache.pekko.cluster.typed.internal.receptionist

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.cluster.{ ConfigValidation, JoinConfigCompatChecker, Valid }

import com.typesafe.config.Config

/**
 * INTERNAL API
 *
 * Verifies that receptionist distributed-key-count are the same across cluster nodes
 */
@InternalApi
private[pekko] final class ClusterReceptionistConfigCompatChecker extends JoinConfigCompatChecker {

  override def requiredKeys = "pekko.cluster.typed.receptionist.distributed-key-count" :: Nil

  override def check(toCheck: Config, actualConfig: Config): ConfigValidation =
    if (toCheck.hasPath(requiredKeys.head))
      JoinConfigCompatChecker.fullMatch(requiredKeys, toCheck, actualConfig)
    else
      Valid // support for rolling update, property doesn't exist in previous versions
}
