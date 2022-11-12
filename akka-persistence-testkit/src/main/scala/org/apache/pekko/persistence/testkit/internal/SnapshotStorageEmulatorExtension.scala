/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.internal

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
private[testkit] object SnapshotStorageEmulatorExtension extends ExtensionId[SnapshotStorage] with ExtensionIdProvider {

  override def get(system: ActorSystem): SnapshotStorage = super.get(system)

  override def createExtension(system: ExtendedActorSystem): SnapshotStorage =
    if (SnapshotTestKit.Settings(system).serialize) {
      new SerializedSnapshotStorageImpl(system)
    } else {
      new SimpleSnapshotStorageImpl
    }

  override def lookup: ExtensionId[_ <: Extension] =
    SnapshotStorageEmulatorExtension
}
