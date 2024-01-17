/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding
import org.apache.pekko
import pekko.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import pekko.annotation.InternalApi
import pekko.util.FlightRecorderLoader

/**
 * INTERNAL API
 */
@InternalApi
object ShardingFlightRecorder extends ExtensionId[ShardingFlightRecorder] with ExtensionIdProvider {

  override def lookup: ExtensionId[? <: Extension] = this

  override def createExtension(system: ExtendedActorSystem): ShardingFlightRecorder =
    FlightRecorderLoader.load[ShardingFlightRecorder](
      system,
      "org.apache.pekko.cluster.sharding.internal.jfr.JFRShardingFlightRecorder",
      NoOpShardingFlightRecorder)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] trait ShardingFlightRecorder extends Extension {
  def rememberEntityOperation(duration: Long): Unit
  def rememberEntityAdd(entityId: String): Unit
  def rememberEntityRemove(entityId: String): Unit
  def entityPassivate(entityId: String): Unit
  def entityPassivateRestart(entityId: String): Unit
}

/**
 * INTERNAL
 */
@InternalApi
private[pekko] case object NoOpShardingFlightRecorder extends ShardingFlightRecorder {
  override def rememberEntityOperation(duration: Long): Unit = ()
  override def rememberEntityAdd(entityId: String): Unit = ()
  override def rememberEntityRemove(entityId: String): Unit = ()
  override def entityPassivate(entityId: String): Unit = ()
  override def entityPassivateRestart(entityId: String): Unit = ()
}
