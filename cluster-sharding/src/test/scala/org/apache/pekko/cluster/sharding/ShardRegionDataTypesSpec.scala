/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.cluster.sharding

import org.apache.pekko
import pekko.cluster.sharding.ShardRegion._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ShardRegionDataTypesSpec extends AnyWordSpec with Matchers {

  "ShardRegionStats" must {

    "be constructable with only stats (failed defaults to empty set)" in {
      val s = ShardRegionStats(Map("s1" -> 3))
      s.stats shouldBe Map("s1" -> 3)
      s.failed shouldBe Set.empty[ShardId]
    }

    "be constructable with both stats and failed" in {
      val s = ShardRegionStats(Map("s1" -> 3, "s2" -> 7), Set("s3"))
      s.stats shouldBe Map("s1" -> 3, "s2" -> 7)
      s.failed shouldBe Set("s3")
    }

    "support equality" in {
      val a = ShardRegionStats(Map("s1" -> 1), Set("s2"))
      val b = ShardRegionStats(Map("s1" -> 1), Set("s2"))
      val c = ShardRegionStats(Map("s1" -> 2), Set("s2"))
      a shouldBe b
      a should not be c
    }

    "support copy" in {
      val original = ShardRegionStats(Map("s1" -> 1), Set("s2"))
      val copied = original.copy(stats = Map("s1" -> 5))
      copied.stats shouldBe Map("s1" -> 5)
      copied.failed shouldBe Set("s2")
    }

    "support pattern matching" in {
      val s = ShardRegionStats(Map("s1" -> 1), Set("s2"))
      s match {
        case ShardRegionStats(stats) =>
          stats shouldBe Map("s1" -> 1)
        case _ => fail("Should match ShardRegionStats")
      }
    }

    "expose Java API getStats and getFailed" in {
      import scala.jdk.CollectionConverters._
      val s = ShardRegionStats(Map("s1" -> 3), Set("s2"))
      s.getStats().asScala shouldBe Map("s1" -> 3)
      s.getFailed().asScala shouldBe Set("s2")
    }
  }

  "CurrentShardRegionState" must {

    "be constructable with only shards (failed defaults to empty set)" in {
      val state = CurrentShardRegionState(Set(ShardState("s1", Set("e1"))))
      state.shards shouldBe Set(ShardState("s1", Set("e1")))
      state.failed shouldBe Set.empty[ShardId]
    }

    "be constructable with both shards and failed" in {
      val state = CurrentShardRegionState(Set(ShardState("s1", Set("e1"))), Set("s2"))
      state.shards shouldBe Set(ShardState("s1", Set("e1")))
      state.failed shouldBe Set("s2")
    }

    "support equality" in {
      val a = CurrentShardRegionState(Set(ShardState("s1", Set("e1"))), Set("s2"))
      val b = CurrentShardRegionState(Set(ShardState("s1", Set("e1"))), Set("s2"))
      val c = CurrentShardRegionState(Set(ShardState("s1", Set("e2"))), Set("s2"))
      a shouldBe b
      a should not be c
    }

    "support copy" in {
      val original = CurrentShardRegionState(Set(ShardState("s1", Set("e1"))), Set("s2"))
      val copied = original.copy(shards = Set(ShardState("s1", Set("e2"))))
      copied.shards shouldBe Set(ShardState("s1", Set("e2")))
      copied.failed shouldBe Set("s2")
    }

    "support pattern matching" in {
      val state = CurrentShardRegionState(Set(ShardState("s1", Set("e1"))), Set("s2"))
      state match {
        case CurrentShardRegionState(shards) =>
          shards shouldBe Set(ShardState("s1", Set("e1")))
        case _ => fail("Should match CurrentShardRegionState")
      }
    }

    "expose Java API getShards and getFailed" in {
      import scala.jdk.CollectionConverters._
      val state = CurrentShardRegionState(Set(ShardState("s1", Set("e1"))), Set("s2"))
      state.getShards().asScala shouldBe Set(ShardState("s1", Set("e1")))
      state.getFailed().asScala shouldBe Set("s2")
    }
  }
}
