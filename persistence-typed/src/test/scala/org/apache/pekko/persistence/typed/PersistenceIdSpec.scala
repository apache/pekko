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

package org.apache.pekko.persistence.typed

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko.actor.testkit.typed.scaladsl.LogCapturing

class PersistenceIdSpec extends AnyWordSpec with Matchers with LogCapturing {

  "PersistenceId" must {
    "use | as default entityIdSeparator for compatibility with Lagom's scaladsl" in {
      PersistenceId("MyType", "abc") should ===(PersistenceId.ofUniqueId("MyType|abc"))
    }

    "support custom separator for compatibility with Lagom's javadsl" in {
      PersistenceId("MyType", "abc", "") should ===(PersistenceId.ofUniqueId("MyTypeabc"))
    }

    "support custom entityIdSeparator for compatibility with other naming" in {
      PersistenceId("MyType", "abc", "#/#") should ===(PersistenceId.ofUniqueId("MyType#/#abc"))
    }

    "not allow | in entityTypeName because it's the default separator" in {
      intercept[IllegalArgumentException] {
        PersistenceId("Invalid | name", "abc")
      }
    }

    "not allow custom separator in entityTypeName" in {
      intercept[IllegalArgumentException] {
        PersistenceId("Invalid name", "abc", " ")
      }
    }

    "not allow | in entityId because it's the default separator" in {
      intercept[IllegalArgumentException] {
        PersistenceId("SomeType", "A|B")
      }
    }

    "not allow custom separator in entityId" in {
      intercept[IllegalArgumentException] {
        PersistenceId("SomeType", "A#B", "#")
      }
    }

    "be able to extract entityTypeHint" in {
      PersistenceId.extractEntityType("SomeType|abc") should ===("SomeType")
      PersistenceId.extractEntityType("abc") should ===("")
      PersistenceId("SomeType", "abc").entityTypeHint should ===("SomeType")
    }

    "be able to extract entityTypeHint from ReplicationId" in {
      val replicaId = ReplicationId("SomeType", "abc", ReplicaId("A"))
      val pid = replicaId.persistenceId
      pid.entityTypeHint should ===("SomeType")
      PersistenceId.extractEntityType(pid.id) should ===("SomeType")
    }

    "be able to extract entityId" in {
      PersistenceId.extractEntityId("SomeType|abc") should ===("abc")
      PersistenceId.extractEntityId("abc") should ===("abc")
      PersistenceId("SomeType", "abc").entityId should ===("abc")
    }

    "extract entityTypeHint and entityId via unapply" in {
      PersistenceId("SomeType", "abc") match {
        case PersistenceId(entityTypeHint, entityId) =>
          entityTypeHint should ===("SomeType")
          entityId should ===("abc")
        case _ => fail()
      }
    }

    "be able to extract entityId from ReplicationId" in {
      val replicaId = ReplicationId("SomeType", "abc", ReplicaId("A"))
      val pid = replicaId.persistenceId
      pid.entityId should ===("abc")
      PersistenceId.extractEntityId(pid.id) should ===("abc")
    }
  }

}
