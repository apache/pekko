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

package org.apache.pekko.stream

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.Promise

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.Deploy
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
import pekko.annotation.InternalApi
import pekko.dispatch.Dispatchers
import pekko.pattern.ask
import pekko.stream.impl.MaterializerGuardian
import pekko.util.JavaDurationConverters._
import pekko.util.Timeout

/**
 * The system materializer is a default materializer to use for most cases running streams, it is a single instance
 * per actor system that is tied to the lifecycle of that system.
 *
 * Not intended to be manually used in user code.
 */
object SystemMaterializer extends ExtensionId[SystemMaterializer] with ExtensionIdProvider {
  override def get(system: ActorSystem): SystemMaterializer = super.get(system)
  override def get(system: ClassicActorSystemProvider): SystemMaterializer = super.get(system)

  override def lookup = SystemMaterializer

  override def createExtension(system: ExtendedActorSystem): SystemMaterializer =
    new SystemMaterializer(system)
}

final class SystemMaterializer(system: ExtendedActorSystem) extends Extension {
  private val systemMaterializerPromise = Promise[Materializer]()

  // load these here so we can share the same instance across materializer guardian and other uses
  /**
   * INTERNAL API
   */
  @InternalApi @nowarn("msg=deprecated")
  private[pekko] val materializerSettings = ActorMaterializerSettings(system)

  private implicit val materializerTimeout: Timeout =
    system.settings.config.getDuration("pekko.stream.materializer.creation-timeout").asScala

  @InternalApi @nowarn("msg=deprecated")
  private val materializerGuardian = system.systemActorOf(
    MaterializerGuardian
      .props(systemMaterializerPromise, materializerSettings)
      // #28037 run on internal dispatcher to make sure default dispatcher starvation doesn't stop materializer creation
      .withDispatcher(Dispatchers.InternalDispatcherId)
      .withDeploy(Deploy.local),
    "Materializers")

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] def createAdditionalSystemMaterializer(): Materializer = {
    val started =
      (materializerGuardian ? MaterializerGuardian.StartMaterializer).mapTo[MaterializerGuardian.MaterializerStarted]
    Await.result(started, materializerTimeout.duration).materializer
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  @nowarn("msg=deprecated")
  private[pekko] def createAdditionalLegacySystemMaterializer(
      namePrefix: String,
      settings: ActorMaterializerSettings): Materializer = {
    val started =
      (materializerGuardian ? MaterializerGuardian.LegacyStartMaterializer(namePrefix, settings))
        .mapTo[MaterializerGuardian.MaterializerStarted]
    Await.result(started, materializerTimeout.duration).materializer
  }

  val materializer: Materializer =
    // block on async creation to make it effectively final
    Await.result(systemMaterializerPromise.future, materializerTimeout.duration)

}
