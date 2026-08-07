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

package org.apache.pekko.cluster.sharding.typed.internal

import scala.annotation.nowarn

import org.apache.pekko
import pekko.cluster.sharding.ShardRegion.{ StartEntity => ClassicStartEntity }
import pekko.cluster.sharding.internal.RememberEntitiesShardStore
import pekko.cluster.sharding.typed.ShardingEnvelope
import pekko.cluster.sharding.typed.ShardingMessageExtractor

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

@nowarn
class ExtractorAdapterSpec extends AnyWordSpecLike with Matchers {

  private val extractor = new ShardingMessageExtractor[String, String] {
    override def entityId(message: String): String =
      if (message.startsWith("entity-")) message.substring(7, message.indexOf(':'))
      else null

    override def shardId(entityId: String): String = entityId.hashCode.abs.toString

    override def unwrapMessage(message: String): String = message.substring(message.indexOf(':') + 1)
  }

  private val adapter = new ExtractorAdapter(extractor)

  "ExtractorAdapter" must {

    "extract entity id from ShardingEnvelope" in {
      adapter.entityId(ShardingEnvelope("entity-1", "hello")) should ===("entity-1")
    }

    "extract entity id from ClassicStartEntity" in {
      adapter.entityId(ClassicStartEntity("entity-1")) should ===("entity-1")
    }

    "delegate to user extractor for user messages" in {
      adapter.entityId("entity-1:hello") should ===("1")
    }

    "return null for RememberEntitiesShardStore.UpdateDone" in {
      adapter.entityId(RememberEntitiesShardStore.UpdateDone(Set("entity-1"), Set.empty)) should be(null)
    }

    "return null for RememberEntitiesShardStore.RememberedEntities" in {
      adapter.entityId(RememberEntitiesShardStore.RememberedEntities(Set("entity-1", "entity-2"))) should be(null)
    }

    "unwrap ShardingEnvelope message" in {
      assert(adapter.unwrapMessage(ShardingEnvelope("entity-1", "hello")) === "hello")
    }

    "unwrap ClassicStartEntity message" in {
      val msg = ClassicStartEntity("entity-1")
      // widen to Any to avoid checkcast to M at the call site (ClassicStartEntity is not actually M)
      assert((adapter.unwrapMessage(msg): Any) === msg)
    }

    "delegate unwrapMessage to user extractor for user messages" in {
      assert(adapter.unwrapMessage("entity-1:hello") === "hello")
    }

    "return null for unwrapMessage on UpdateDone" in {
      assert(adapter.unwrapMessage(RememberEntitiesShardStore.UpdateDone(Set("entity-1"), Set.empty)) === null)
    }

    "return null for unwrapMessage on RememberedEntities" in {
      assert(adapter.unwrapMessage(RememberEntitiesShardStore.RememberedEntities(Set("entity-1"))) === null)
    }
  }
}
