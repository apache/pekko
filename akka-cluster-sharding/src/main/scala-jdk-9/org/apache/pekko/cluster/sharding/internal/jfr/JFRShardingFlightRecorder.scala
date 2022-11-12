/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.internal.jfr

import org.apache.pekko.cluster.sharding.ShardingFlightRecorder

class JFRShardingFlightRecorder extends ShardingFlightRecorder {
  override def rememberEntityOperation(duration: Long): Unit =
    new RememberEntityWrite(duration).commit()
  override def rememberEntityAdd(entityId: String): Unit =
    new RememberEntityAdd(entityId).commit()
  override def rememberEntityRemove(entityId: String): Unit =
    new RememberEntityRemove(entityId).commit()
  override def entityPassivate(entityId: String): Unit =
    new Passivate(entityId).commit()
  override def entityPassivateRestart(entityId: String): Unit =
    new PassivateRestart(entityId).commit()
}
