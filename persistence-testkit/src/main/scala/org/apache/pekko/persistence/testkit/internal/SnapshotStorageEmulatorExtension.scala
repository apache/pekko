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
import pekko.persistence.testkit.SnapshotStorage
import pekko.persistence.testkit.scaladsl.SnapshotTestKit

/**
 * INTERNAL API
 */
@InternalApi
private[testkit] object SnapshotStorageEmulatorExtension extends ExtensionId[SnapshotStorageEmulatorExtension]
    with ExtensionIdProvider {

  override def get(system: ActorSystem): SnapshotStorageEmulatorExtension = super.get(system)

  override def createExtension(system: ExtendedActorSystem): SnapshotStorageEmulatorExtension =
    new SnapshotStorageEmulatorExtension(system)

  override def lookup: ExtensionId[_ <: Extension] =
    SnapshotStorageEmulatorExtension
}

/**
 * INTERNAL API
 */
@InternalApi
final class SnapshotStorageEmulatorExtension(system: ExtendedActorSystem) extends Extension {
  private val stores = new ConcurrentHashMap[String, SnapshotStorage]()
  private lazy val shouldCreateSerializedSnapshotStorage = SnapshotTestKit.Settings(system).serialize

  def storageFor(key: String): SnapshotStorage =
    stores.computeIfAbsent(key,
      _ => {
        // we don't really care about the key here, we just want separate instances
        if (shouldCreateSerializedSnapshotStorage) {
          new SerializedSnapshotStorageImpl(system)
        } else {
          new SimpleSnapshotStorageImpl
        }
      })
}
