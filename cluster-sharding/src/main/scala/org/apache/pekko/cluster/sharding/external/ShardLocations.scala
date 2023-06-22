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

package org.apache.pekko.cluster.sharding.external

import org.apache.pekko
import pekko.cluster.sharding.ShardRegion.ShardId
import pekko.cluster.sharding.external.ExternalShardAllocationStrategy.ShardLocation
import pekko.util.ccompat.JavaConverters._

final class ShardLocations(val locations: Map[ShardId, ShardLocation]) {

  /**
   * Java API
   */
  def getShardLocations(): java.util.Map[ShardId, ShardLocation] = locations.asJava
}
