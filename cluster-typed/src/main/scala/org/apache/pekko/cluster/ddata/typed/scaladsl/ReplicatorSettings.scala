/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata.typed.scaladsl

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.adapter._
import pekko.annotation.InternalApi
import pekko.cluster.{ ddata => dd }

/**
 * @see [[pekko.cluster.ddata.ReplicatorSettings]].
 */
object ReplicatorSettings {

  /**
   * Create settings from the default configuration
   * `pekko.cluster.distributed-data`.
   */
  def apply(system: ActorSystem[_]): ReplicatorSettings =
    dd.ReplicatorSettings(system.toClassic)

  /**
   * Create settings from a configuration with the same layout as
   * the default configuration `pekko.cluster.distributed-data`.
   */
  def apply(config: Config): ReplicatorSettings =
    dd.ReplicatorSettings(config)

  /**
   * INTERNAL API
   * The name of the actor used in DistributedData extensions.
   */
  @InternalApi private[pekko] def name(system: ActorSystem[_]): String =
    dd.ReplicatorSettings.name(system.toClassic, Some("typed"))
}
