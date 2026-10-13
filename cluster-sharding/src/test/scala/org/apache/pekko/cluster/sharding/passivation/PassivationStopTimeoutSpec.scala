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

package org.apache.pekko.cluster.sharding.passivation

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.sharding.ClusterSharding
import pekko.cluster.sharding.ClusterShardingSettings
import pekko.cluster.sharding.ShardRegion
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe
import pekko.testkit.WithLogCapturing

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object PassivationStopTimeoutSpec {

  val config: Config = ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.cluster.sharding.verbose-debug-logging = on
    pekko.cluster.sharding.fail-on-invalid-entity-state-transition = on
    pekko.cluster.sharding.passivation.strategy = none
    """)

  val stopTimeoutConfig: Config =
    ConfigFactory.parseString("pekko.cluster.sharding.passivation.stop-timeout = 1s").withFallback(config)

  val noStopTimeoutConfig: Config =
    ConfigFactory.parseString("pekko.cluster.sharding.passivation.stop-timeout = off").withFallback(config)

  object Entity {
    // the entity never stops itself when receiving this stop message
    case object IgnoredStop
    case object ManuallyPassivate
    final case class Envelope(id: String, message: Any)
    final case class Started(id: String)
    final case class Received(id: String, message: Any)

    def props(probe: ActorRef): Props = Props(new Entity(probe))
  }

  class Entity(probe: ActorRef) extends Actor {
    private def id = self.path.name

    override def preStart(): Unit = probe ! Entity.Started(id)

    def receive: Receive = {
      case Entity.ManuallyPassivate =>
        probe ! Entity.Received(id, Entity.ManuallyPassivate)
        context.parent ! ShardRegion.Passivate(Entity.IgnoredStop)
      case msg => probe ! Entity.Received(id, msg)
    }
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case Entity.Envelope(id, message) => (id, message)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case Entity.Envelope(id, _) => id
    case _                      => throw new IllegalArgumentException
  }
}

abstract class AbstractPassivationStopTimeoutSpec(config: Config) extends PekkoSpec(config) with WithLogCapturing {
  import PassivationStopTimeoutSpec._

  val probe: TestProbe = TestProbe()

  def start(): ActorRef = {
    // single node cluster
    Cluster(system).join(Cluster(system).selfAddress)
    val settings = ClusterShardingSettings(system)
    ClusterSharding(system).start(
      "myType",
      Entity.props(probe.ref),
      settings,
      extractEntityId,
      extractShardId,
      ClusterSharding(system).defaultShardAllocationStrategy(settings),
      Entity.IgnoredStop)
  }

  def passivateEntityThatDoesNotStop(region: ActorRef): Unit = {
    region ! Entity.Envelope("1", "A")
    probe.expectMsg(Entity.Started("1"))
    probe.expectMsg(Entity.Received("1", "A"))

    region ! Entity.Envelope("1", Entity.ManuallyPassivate)
    probe.expectMsg(Entity.Received("1", Entity.ManuallyPassivate))
    probe.expectMsg(Entity.Received("1", Entity.IgnoredStop))

    // buffered by the shard while the entity is passivating
    region ! Entity.Envelope("1", "B")
  }
}

class PassivationStopTimeoutSpec
    extends AbstractPassivationStopTimeoutSpec(PassivationStopTimeoutSpec.stopTimeoutConfig) {
  import PassivationStopTimeoutSpec._

  "Passivation of an entity that does not stop" must {
    "stop the entity after the stop timeout and deliver buffered messages to a new incarnation" in {
      val region = start()
      passivateEntityThatDoesNotStop(region)
      probe.expectNoMessage(500.millis)
      probe.expectMsg(5.seconds, Entity.Started("1"))
      probe.expectMsg(Entity.Received("1", "B"))

      // the new incarnation is active and receives messages directly
      region ! Entity.Envelope("1", "C")
      probe.expectMsg(Entity.Received("1", "C"))
    }
  }
}

class PassivationStopTimeoutOffSpec
    extends AbstractPassivationStopTimeoutSpec(PassivationStopTimeoutSpec.noStopTimeoutConfig) {

  "Passivation of an entity that does not stop" must {
    "keep the entity passivating when the stop timeout is off" in {
      val region = start()
      passivateEntityThatDoesNotStop(region)
      probe.expectNoMessage(2.seconds)
    }
  }
}
