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

package org.apache.pekko.persistence.testkit.internal

import java.util.concurrent.ConcurrentHashMap

import org.apache.pekko
import pekko.actor.Extension
import pekko.actor.{ ActorSystem, ExtendedActorSystem, ExtensionId, ExtensionIdProvider }
import pekko.annotation.InternalApi
import pekko.persistence.testkit.EventStorage
import pekko.persistence.testkit.JournalOperation
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.ProcessingPolicy
import pekko.persistence.testkit.scaladsl.PersistenceTestKit

/**
 * INTERNAL API
 */
@InternalApi
private[testkit] object InMemStorageExtension extends ExtensionId[InMemStorageExtension] with ExtensionIdProvider {

  override def get(system: ActorSystem): InMemStorageExtension = super.get(system)

  override def createExtension(system: ExtendedActorSystem): InMemStorageExtension =
    new InMemStorageExtension(system)

  override def lookup = InMemStorageExtension

}

/**
 * INTERNAL API
 */
@InternalApi
final class InMemStorageExtension(system: ExtendedActorSystem) extends Extension {

  private val stores = new ConcurrentHashMap[String, EventStorage]()

  def defaultStorage(): EventStorage = storageFor(PersistenceTestKitPlugin.PluginId)

  // shortcuts for default policy
  def currentPolicy: ProcessingPolicy[JournalOperation] = defaultStorage().currentPolicy
  def setPolicy(policy: ProcessingPolicy[JournalOperation]): Unit = defaultStorage().setPolicy(policy)
  def resetPolicy(): Unit = defaultStorage().resetPolicy()

  def storageFor(key: String): EventStorage =
    stores.computeIfAbsent(key,
      _ =>
        // we don't really care about the key here, we just want separate instances
        if (PersistenceTestKit.Settings(system).serialize) {
          new SerializedEventStorageImpl(system)
        } else {
          new SimpleEventStorageImpl
        }
    )

}
