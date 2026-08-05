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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.cluster.sharding.ShardRegion.{ StartEntity => ClassicStartEntity }
import pekko.cluster.sharding.internal.RememberEntitiesShardStore
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey

class ExtractorAdapterSpec extends AnyWordSpecLike with Matchers {

  val typeKey = EntityTypeKey[String]("test")

  val extractor = new ShardingMessageExtractor[String, String] {
    override def entityId(message: String): String =
      if (message.startsWith("entity-")) message.substring(7, message.indexOf(':'))
      else null
    override def shardId(entityId: String): String = entityId.hashCode.abs.toString
    override def unwrapMessage(message: String): String = message.substring(message.indexOf(':') + 1)
  }

  val adapter = new ExtractorAdapter(extractor)

  "ExtractorAdapter" must {

    "extract entity id from ShardingEnvelope" in {
      val envelope = ShardingEnvelope("entity-1", "hello")
      adapter.entityId(envelope) should ===("entity-1")
    }

    "extract entity id from ClassicStartEntity" in {
      val msg = ClassicStartEntity("entity-1")
      adapter.entityId(msg) should ===("entity-1")
    }

    "delegate to user extractor for user messages" in {
      adapter.entityId("entity-1:hello") should ===("1")
    }

    "return null for RememberEntitiesShardStore.UpdateDone" in {
      val msg = RememberEntitiesShardStore.UpdateDone(Set("entity-1"), Set.empty)
      adapter.entityId(msg) should be(null)
    }

    "return null for RememberEntitiesShardStore.RememberedEntities" in {
      val msg = RememberEntitiesShardStore.RememberedEntities(Set("entity-1", "entity-2"))
      adapter.entityId(msg) should be(null)
    }

    "unwrap ShardingEnvelope message" in {
      val envelope = ShardingEnvelope("entity-1", "hello")
      adapter.unwrapMessage(envelope) should ===("hello")
    }

    "unwrap ClassicStartEntity message" in {
      val msg = ClassicStartEntity("entity-1")
      adapter.unwrapMessage(msg) should ===(msg)
    }

    "delegate unwrapMessage to user extractor for user messages" in {
      adapter.unwrapMessage("entity-1:hello") should ===("hello")
    }

    "return null.unwrapMessage for UpdateDone" in {
      val msg = RememberEntitiesShardStore.UpdateDone(Set("entity-1"), Set.empty)
      adapter.unwrapMessage(msg) should be(null)
    }

    "return null.unwrapMessage for RememberedEntities" in {
      val msg = RememberEntitiesShardStore.RememberedEntities(Set("entity-1"))
      adapter.unwrapMessage(msg) should be(null)
    }
  }
}
