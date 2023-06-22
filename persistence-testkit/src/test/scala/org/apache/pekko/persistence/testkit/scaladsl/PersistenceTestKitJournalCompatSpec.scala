/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.scaladsl

import java.io.NotSerializableException

import org.apache.pekko
import pekko.persistence.CapabilityFlag
import pekko.persistence.journal.JournalSpec
import pekko.persistence.snapshot.SnapshotStoreSpec
import pekko.persistence.testkit._
import pekko.persistence.testkit.EventStorage.JournalPolicies
import pekko.persistence.testkit.Reject
import pekko.persistence.testkit.internal.InMemStorageExtension

class PersistenceTestKitJournalCompatSpec extends JournalSpec(config = PersistenceTestKitPlugin.config) {

  override def beforeAll(): Unit = {
    super.beforeAll()
    InMemStorageExtension(system).setPolicy(new JournalPolicies.PolicyType {
      override def tryProcess(persistenceId: String, op: JournalOperation): ProcessingResult = {
        op match {
          case WriteEvents(batch) =>
            val allSerializable =
              batch.filter(_.isInstanceOf[AnyRef]).forall(_.isInstanceOf[java.io.Serializable])
            if (allSerializable) {
              ProcessingSuccess
            } else {
              Reject(new NotSerializableException("Some objects in the batch were not serializable"))
            }
          case _ => ProcessingSuccess
        }

      }
    })
  }

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true
  override protected def supportsMetadata: CapabilityFlag = true
}

class PersistenceTestKitSnapshotStoreCompatSpec
    extends SnapshotStoreSpec(config = PersistenceTestKitSnapshotPlugin.config)
