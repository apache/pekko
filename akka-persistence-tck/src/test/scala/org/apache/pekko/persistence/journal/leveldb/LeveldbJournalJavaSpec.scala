/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.journal.leveldb

import org.apache.pekko
import pekko.persistence.{ PersistenceSpec, PluginCleanup }
import pekko.persistence.journal.JournalSpec

class LeveldbJournalJavaSpec
    extends JournalSpec(
      config = PersistenceSpec.config(
        "leveldb",
        "LeveldbJournalJavaSpec",
        extraConfig = Some("""
        akka.persistence.journal.leveldb.native = off
        akka.actor.allow-java-serialization = off
        akka.actor.warn-about-java-serializer-usage = on
        """)))
    with PluginCleanup {

  override def supportsRejectingNonSerializableObjects = true

  override def supportsSerialization = true
}
