/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ClusterLogClass {

  val ClusterCore: Class[Cluster] = classOf[Cluster]
  val ClusterHeartbeat: Class[ClusterHeartbeat] = classOf[ClusterHeartbeat]
  val ClusterGossip: Class[ClusterGossip] = classOf[ClusterGossip]

}

/**
 * INTERNAL API: Logger class for (verbose) heartbeat logging.
 */
@InternalApi private[pekko] class ClusterHeartbeat

/**
 * INTERNAL API: Logger class for (verbose) gossip logging.
 */
@InternalApi private[pekko] class ClusterGossip
