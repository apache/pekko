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

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.cluster.ddata.DurableStore
import pekko.cluster.ddata.Replicator
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActors
import pekko.testkit.TestProbe
import pekko.testkit.WithLogCapturing

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object ClusterShardingGuardianSpec {
  final case class LoadStarted(store: ActorRef, replicator: ActorRef)
  case object FailLoad
  case object CompleteLoad

  class ControlledDurableStore(@nowarn("msg=never used") config: Config) extends Actor {
    def receive: Receive = {
      case DurableStore.LoadAll =>
        context.system.eventStream.publish(LoadStarted(self, sender()))
      case FailLoad =>
        throw new DurableStore.LoadFailed("test load failure")
      case CompleteLoad =>
        context.parent ! DurableStore.LoadAllCompleted
      case DurableStore.Store(_, _, reply) =>
        reply.foreach(r => r.replyTo ! r.successMsg)
    }
  }

  val config = ConfigFactory.parseString(s"""
    pekko.actor.provider = cluster
    pekko.remote.artery.canonical.port = 0
    pekko.cluster.sharding.state-store-mode = ddata
    pekko.cluster.sharding.remember-entities = on
    pekko.cluster.sharding.distributed-data.durable {
      keys = ["*"]
      store-actor-class = "${classOf[ControlledDurableStore].getName}"
    }
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
  """)
}

class ClusterShardingGuardianSpec
    extends PekkoSpec(ClusterShardingGuardianSpec.config)
    with ImplicitSender
    with WithLogCapturing {

  import ClusterShardingGuardianSpec._

  private val cluster = Cluster(system)

  override protected def atStartup(): Unit = {
    cluster.join(cluster.selfAddress)
    awaitAssert(cluster.selfMember.status shouldEqual MemberStatus.Up)
  }

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case i: Int => (i.toString, i)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case i: Int                      => (i % 10).toString
    case ShardRegion.StartEntity(id) => (id.toInt % 10).toString
    case _                           => "0"
  }

  "ClusterShardingGuardian" should {

    "create a fresh replicator after its durable store fails to load" in {
      val loadProbe = TestProbe()
      system.eventStream.subscribe(loadProbe.ref, classOf[LoadStarted])
      val sharding = ClusterSharding(system)
      val firstRegion = sharding.start(
        "type-a",
        TestActors.echoActorProps,
        ClusterShardingSettings(system),
        extractEntityId,
        extractShardId)

      val initialLoad = loadProbe.expectMsgType[LoadStarted]
      watch(initialLoad.replicator)
      initialLoad.store ! FailLoad
      expectTerminated(initialLoad.replicator)
      system.stop(firstRegion)

      val nextRegion = sharding.start(
        "type-b",
        TestActors.echoActorProps,
        ClusterShardingSettings(system),
        extractEntityId,
        extractShardId)

      val nextLoad = loadProbe.expectMsgType[LoadStarted]
      (nextLoad.replicator should not).equal(initialLoad.replicator)
      nextLoad.store ! CompleteLoad
      nextLoad.replicator ! Replicator.GetKeyIds
      expectMsgType[Replicator.GetKeyIdsResult]
      nextRegion ! 1
      expectMsg(1)
      system.eventStream.unsubscribe(loadProbe.ref)
    }
  }
}
