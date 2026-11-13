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

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ Props, ReceiveTimeout }
import pekko.cluster.sharding.ShardCoordinator.Internal.{ BeginHandOff, BeginHandOffAck, HandOff, ShardStopped }
import pekko.cluster.sharding.ShardCoordinator.RebalanceWorker
import pekko.cluster.sharding.ShardCoordinator.RebalanceWorker.ShardRegionTerminated
import pekko.testkit.{ PekkoSpec, TestActorRef, TestProbe }

class RebalanceWorkerSpec extends PekkoSpec {
  private val shard = "1"

  private class Fixture(isRebalance: Boolean) {
    val owner = TestProbe()
    val other = TestProbe()
    val result = TestProbe()
    val parent = TestProbe()
    val worker = TestActorRef[RebalanceWorker](
      Props(new RebalanceWorker("test", shard, owner.ref, 1.minute, Set(owner.ref, other.ref), isRebalance) {
        override def done(ok: Boolean): Unit = {
          result.ref ! ok
          super.done(ok)
        }
      }),
      parent.ref)
    result.watch(worker)
    owner.expectMsg(BeginHandOff(shard))
    other.expectMsg(BeginHandOff(shard))

    def expectResult(ok: Boolean): Unit = {
      result.expectMsg(ok)
      result.expectTerminated(worker)
    }

    def ackOwner(): Unit = worker.tell(BeginHandOffAck(shard), owner.ref)
    def ackOther(): Unit = worker.tell(BeginHandOffAck(shard), other.ref)
    def ownerTerminated(): Unit = worker ! ShardRegionTerminated(owner.ref)
  }

  for (isRebalance <- List(true, false)) {
    s"RebalanceWorker (isRebalance=$isRebalance)" must {
      "complete after the remaining acknowledgements when the owner terminates before acknowledging" in {
        val f = new Fixture(isRebalance)
        f.ownerTerminated()
        f.result.expectNoMessage(20.millis)
        f.ackOther()
        f.expectResult(true)
        f.owner.expectNoMessage(20.millis)
      }

      "remember owner termination after its acknowledgement" in {
        val f = new Fixture(isRebalance)
        f.ackOwner()
        f.ownerTerminated()
        f.result.expectNoMessage(20.millis)
        f.ackOther()
        f.expectResult(true)
        f.owner.expectNoMessage(20.millis)
      }

      "complete when owner termination supplies the final acknowledgement" in {
        val f = new Fixture(isRebalance)
        f.ackOther()
        f.ownerTerminated()
        f.expectResult(true)
        f.owner.expectNoMessage(20.millis)
      }

      "complete when the remaining region terminates after the owner" in {
        val f = new Fixture(isRebalance)
        f.ownerTerminated()
        f.result.expectNoMessage(20.millis)
        f.worker ! ShardRegionTerminated(f.other.ref)
        f.expectResult(true)
        f.owner.expectNoMessage(20.millis)
      }

      "still hand off to a live owner when a different region terminates" in {
        val f = new Fixture(isRebalance)
        f.worker ! ShardRegionTerminated(f.other.ref)
        f.ackOwner()
        f.owner.expectMsg(HandOff(shard))
        f.worker.tell(ShardStopped(shard), f.owner.ref)
        f.expectResult(true)
      }

      "complete the normal handoff" in {
        val f = new Fixture(isRebalance)
        f.ackOwner()
        f.ackOther()
        f.owner.expectMsg(HandOff(shard))
        f.worker.tell(ShardStopped(shard), f.owner.ref)
        f.expectResult(true)
      }

      "complete when the owner terminates after handoff begins" in {
        val f = new Fixture(isRebalance)
        f.ackOwner()
        f.ackOther()
        f.owner.expectMsg(HandOff(shard))
        f.ownerTerminated()
        f.expectResult(true)
      }

      "time out while another region has not acknowledged even if the owner has terminated" in {
        val f = new Fixture(isRebalance)
        f.ownerTerminated()
        f.worker ! ReceiveTimeout(1.minute)
        f.expectResult(false)
      }

      "time out waiting for a live owner to stop the shard" in {
        val f = new Fixture(isRebalance)
        f.ackOwner()
        f.ackOther()
        f.owner.expectMsg(HandOff(shard))
        f.worker ! ReceiveTimeout(1.minute)
        f.expectResult(false)
      }
    }
  }
}
