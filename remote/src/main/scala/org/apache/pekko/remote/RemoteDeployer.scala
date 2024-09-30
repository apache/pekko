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

package org.apache.pekko.remote

import com.typesafe.config._

import org.apache.pekko
import pekko.ConfigurationException
import pekko.actor._
import pekko.remote.routing.RemoteRouterConfig
import pekko.routing._
import pekko.routing.Pool
import pekko.util.ccompat.JavaConverters._

@SerialVersionUID(1L)
final case class RemoteScope(node: Address) extends Scope {
  def withFallback(other: Scope): Scope = this
}

/**
 * INTERNAL API
 */
private[pekko] class RemoteDeployer(_settings: ActorSystem.Settings, _pm: DynamicAccess)
    extends Deployer(_settings, _pm) {
  override def parseConfig(path: String, config: Config): Option[Deploy] = {

    super.parseConfig(path, config) match {
      case d @ Some(deploy) =>
        deploy.config.getString("remote") match {
          case AddressFromURIString(r) => Some(deploy.copy(scope = RemoteScope(r)))
          case str if !str.isEmpty     => throw new ConfigurationException(s"unparseable remote node name [$str]")
          case _ =>
            val nodes = deploy.config.getStringList("target.nodes").asScala.map(AddressFromURIString(_))
            if (nodes.isEmpty || deploy.routerConfig == NoRouter) d
            else
              deploy.routerConfig match {
                case r: Pool => Some(deploy.copy(routerConfig = RemoteRouterConfig(r, nodes)))
                case _       => d
              }
        }
      case None => None
    }
  }
}
