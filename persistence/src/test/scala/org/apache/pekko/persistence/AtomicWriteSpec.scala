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

package org.apache.pekko.persistence

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AtomicWriteSpec extends AnyWordSpec with Matchers {

  "AtomicWrite" must {
    "only contain messages for the same persistence id" in {
      AtomicWrite(
        PersistentRepr("", 1, "p1") ::
        PersistentRepr("", 2, "p1") :: Nil).persistenceId should ===("p1")

      intercept[IllegalArgumentException] {
        AtomicWrite(
          PersistentRepr("", 1, "p1") ::
          PersistentRepr("", 2, "p1") ::
          PersistentRepr("", 3, "p2") :: Nil)
      }
    }

    "have highestSequenceNr" in {
      AtomicWrite(
        PersistentRepr("", 1, "p1") ::
        PersistentRepr("", 2, "p1") ::
        PersistentRepr("", 3, "p1") :: Nil).highestSequenceNr should ===(3)
    }

    "have lowestSequenceNr" in {
      AtomicWrite(
        PersistentRepr("", 2, "p1") ::
        PersistentRepr("", 3, "p1") ::
        PersistentRepr("", 4, "p1") :: Nil).lowestSequenceNr should ===(2)
    }
  }

}
