/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata.typed.javadsl

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.adapter._
import pekko.cluster.{ ddata => dd }

object ReplicatorSettings {

  /**
   * Create settings from the default configuration
   * `akka.cluster.distributed-data`.
   */
  def create(system: ActorSystem[_]): dd.ReplicatorSettings =
    dd.ReplicatorSettings(system.toClassic)

  /**
   * Create settings from a configuration with the same layout as
   * the default configuration `akka.cluster.distributed-data`.
   */
  def create(config: Config): dd.ReplicatorSettings =
    dd.ReplicatorSettings(config)
}
