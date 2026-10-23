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

package org.apache.pekko.persistence

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.persistence.JournalProtocol.WriteMessages

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WriteMessagesSpec extends AnyWordSpec with Matchers {

  private val writes = AtomicWrite(PersistentRepr("a", 1, "p1")) :: Nil

  "PersistentRepr" must {
    "have a hashCode without a sender" in {
      val repr = PersistentRepr("a", 1, "p1")
      repr.sender should ===(ActorRef.noSender)
      repr.hashCode should ===(PersistentRepr("a", 1, "p1").hashCode)
    }
  }

  "JournalProtocol.WriteMessages" must {
    "not bypass the circuit breaker by default" in {
      WriteMessages(writes, ActorRef.noSender, 1).bypassCircuitBreaker should ===(false)
      new WriteMessages(writes, ActorRef.noSender, 1).bypassCircuitBreaker should ===(false)
      WriteMessages(writes, ActorRef.noSender, 1, bypassCircuitBreaker = true).bypassCircuitBreaker should ===(true)
    }

    "keep bypassCircuitBreaker in copy" in {
      val w = WriteMessages(writes, ActorRef.noSender, 1, bypassCircuitBreaker = true)
      val copied = w.copy(actorInstanceId = 2)
      copied.actorInstanceId should ===(2)
      copied.bypassCircuitBreaker should ===(true)
      w.withBypassCircuitBreaker(false).bypassCircuitBreaker should ===(false)
    }

    "extract the first 3 fields in pattern matching" in {
      val w: Any = WriteMessages(writes, ActorRef.noSender, 3, bypassCircuitBreaker = true)
      w match {
        case WriteMessages(messages, persistentActor, actorInstanceId) =>
          messages should ===(writes)
          persistentActor should ===(ActorRef.noSender)
          actorInstanceId should ===(3)
        case other =>
          fail(s"unexpected $other")
      }
    }

    "include bypassCircuitBreaker in equality" in {
      WriteMessages(writes, ActorRef.noSender, 1) should ===(WriteMessages(writes, ActorRef.noSender, 1))
      WriteMessages(writes, ActorRef.noSender, 1).hashCode should ===(
        WriteMessages(writes, ActorRef.noSender, 1).hashCode)
      WriteMessages(writes, ActorRef.noSender, 1) should !==(
        WriteMessages(writes, ActorRef.noSender, 1, bypassCircuitBreaker = true))
    }

    "have product elements for the first 3 fields" in {
      val w = WriteMessages(writes, ActorRef.noSender, 1, bypassCircuitBreaker = true)
      w.productArity should ===(3)
      w.productIterator.toList should ===(List(writes, ActorRef.noSender, 1))
    }
  }
}
