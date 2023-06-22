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

package org.apache.pekko.cluster.sharding.typed

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.Behavior
import pekko.actor.typed.receptionist.Receptionist
import pekko.actor.typed.receptionist.ServiceKey
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.Routers
import pekko.cluster.MultiNodeClusterSpec
import pekko.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import pekko.cluster.typed.MultiNodeTypedClusterSpec
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.serialization.jackson.CborSerializable

object ShardedDaemonProcessSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  val SnitchServiceKey = ServiceKey[AnyRef]("snitch")

  case class ProcessActorEvent(id: Int, event: Any) extends CborSerializable

  object ProcessActor {
    trait Command
    case object Stop extends Command

    def apply(id: Int): Behavior[Command] = Behaviors.setup { ctx =>
      val snitchRouter = ctx.spawn(Routers.group(SnitchServiceKey), "router")
      snitchRouter ! ProcessActorEvent(id, "Started")

      Behaviors.receiveMessagePartial {
        case Stop =>
          snitchRouter ! ProcessActorEvent(id, "Stopped")
          Behaviors.stopped
      }
    }
  }

  commonConfig(ConfigFactory.parseString("""
        pekko.loglevel = DEBUG
        pekko.cluster.sharded-daemon-process {
          sharding {
            # First is likely to be ignored as shard coordinator not ready
            retry-interval = 0.2s
          }
          # quick ping to make test swift
          keep-alive-interval = 1s
        }
      """).withFallback(MultiNodeClusterSpec.clusterConfig))

}

class ShardedDaemonProcessMultiJvmNode1 extends ShardedDaemonProcessSpec
class ShardedDaemonProcessMultiJvmNode2 extends ShardedDaemonProcessSpec
class ShardedDaemonProcessMultiJvmNode3 extends ShardedDaemonProcessSpec

abstract class ShardedDaemonProcessSpec
    extends MultiNodeSpec(ShardedDaemonProcessSpec)
    with MultiNodeTypedClusterSpec
    with ScalaFutures {

  import ShardedDaemonProcessSpec._

  val probe: TestProbe[AnyRef] = TestProbe[AnyRef]()

  "Cluster sharding in multi dc cluster" must {
    "form cluster" in {
      formCluster(first, second, third)
      runOn(first) {
        typedSystem.receptionist ! Receptionist.Register(SnitchServiceKey, probe.ref, probe.ref)
        probe.expectMessageType[Receptionist.Registered]
      }
      enterBarrier("snitch-registered")

      probe.awaitAssert({
          typedSystem.receptionist ! Receptionist.Find(SnitchServiceKey, probe.ref)
          probe.expectMessageType[Receptionist.Listing].serviceInstances(SnitchServiceKey).size should ===(1)
        }, 5.seconds)
      enterBarrier("snitch-seen")
    }

    "init actor set" in {
      ShardedDaemonProcess(typedSystem).init("the-fearless", 4, id => ProcessActor(id))
      enterBarrier("sharded-daemon-process-initialized")
      runOn(first) {
        val startedIds = (0 to 3).map { _ =>
          val event = probe.expectMessageType[ProcessActorEvent](5.seconds)
          event.event should ===("Started")
          event.id
        }.toSet
        startedIds.size should ===(4)
      }
      enterBarrier("sharded-daemon-process-started")
    }

    // FIXME test removing one cluster node and verify all are alive (how do we do that?)

  }
}
