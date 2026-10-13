/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.internal

import java.time.Instant

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ShardedDaemonProcessStateSpec extends AnyWordSpecLike with Matchers {

  "The sharded daemon process state" should {

    "merge highest revision" in {
      val rev1 = ShardedDaemonProcessState(1L, 2, true, Instant.now())
      val rev2 = ShardedDaemonProcessState(2L, 2, true, Instant.now())
      rev1.merge(rev2) shouldBe theSameInstanceAs(rev2)
      rev2.merge(rev1) shouldBe theSameInstanceAs(rev2)
      rev1.merge(rev1) shouldBe theSameInstanceAs(rev1)
      rev2.merge(rev2) shouldBe theSameInstanceAs(rev2)
    }

    "merge same revision, completion" in {
      val incomplete = ShardedDaemonProcessState(2L, 2, false, Instant.now())
      val complete = ShardedDaemonProcessState(2L, 2, true, Instant.now())
      complete.merge(incomplete) shouldBe theSameInstanceAs(complete)
      incomplete.merge(complete) shouldBe theSameInstanceAs(complete)
      incomplete.merge(incomplete) shouldBe theSameInstanceAs(incomplete)
      complete.merge(complete) shouldBe theSameInstanceAs(complete)
    }

  }

}
