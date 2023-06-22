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

package org.apache.pekko.remote.artery.compress

import org.apache.pekko
import pekko.actor._
import pekko.testkit.PekkoSpec

class OutboundCompressionSpec extends PekkoSpec {
  import CompressionTestUtils._

  "Outbound ActorRef compression" must {
    val alice = minimalRef("alice")
    val bob = minimalRef("bob")

    "not compress unknown actor ref" in {
      val table = CompressionTable.empty[ActorRef]
      table.compress(alice) should ===(-1) // not compressed
    }

    "compress previously registered actor ref" in {
      val table = CompressionTable(17L, 1, Map(system.deadLetters -> 0, alice -> 1))
      table.compress(alice) should ===(1) // compressed
      table.compress(bob) should ===(-1) // not compressed

      val table2 = CompressionTable(table.originUid, table.version, table.dictionary.updated(bob, 2))
      table2.compress(alice) should ===(1) // compressed
      table2.compress(bob) should ===(2) // compressed
    }
  }

}
