/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import scala.concurrent.Promise

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.Props
import pekko.annotation.InternalApi
import pekko.stream.ActorMaterializerSettings
import pekko.stream.Materializer

/**
 * INTERNAL API
 *
 * The materializer guardian is parent to all materializers created on the `system` level including the default
 * system wide materializer. Eagerly started by the SystemMaterializer extension on system startup.
 */
@InternalApi
private[pekko] object MaterializerGuardian {

  case object StartMaterializer
  final case class MaterializerStarted(materializer: Materializer)

  // this is available to keep backwards compatibility with ActorMaterializer and should
  // be removed together with ActorMaterialixer in Akka 2.7
  final case class LegacyStartMaterializer(namePrefix: String, settings: ActorMaterializerSettings)

  def props(systemMaterializer: Promise[Materializer], materializerSettings: ActorMaterializerSettings) =
    Props(new MaterializerGuardian(systemMaterializer, materializerSettings))
}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
@InternalApi
private[pekko] final class MaterializerGuardian(
    systemMaterializerPromise: Promise[Materializer],
    materializerSettings: ActorMaterializerSettings)
    extends Actor {
  import MaterializerGuardian._

  private val defaultAttributes = materializerSettings.toAttributes
  private val defaultNamePrefix = "flow"

  private val systemMaterializer = startMaterializer(defaultNamePrefix, None)
  systemMaterializerPromise.success(systemMaterializer)

  override def receive: Receive = {
    case StartMaterializer =>
      sender() ! MaterializerStarted(startMaterializer(defaultNamePrefix, None))
    case LegacyStartMaterializer(namePrefix, settings) =>
      sender() ! MaterializerStarted(startMaterializer(namePrefix, Some(settings)))
  }

  private def startMaterializer(namePrefix: String, settings: Option[ActorMaterializerSettings]) = {
    val attributes = settings match {
      case None                         => defaultAttributes
      case Some(`materializerSettings`) => defaultAttributes
      case Some(settings)               => settings.toAttributes
    }

    PhasedFusingActorMaterializer(context, namePrefix, settings.getOrElse(materializerSettings), attributes)
  }
}
