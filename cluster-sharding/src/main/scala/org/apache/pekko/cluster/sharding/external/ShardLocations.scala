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
