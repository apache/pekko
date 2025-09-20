/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, Address, Props }
import pekko.cluster.Cluster
import pekko.cluster.sharding.ExternalShardAllocationSpec.GiveMeYourHome.{ Get, Home }
import pekko.cluster.sharding.external.{ ExternalShardAllocation, ExternalShardAllocationStrategy }
import pekko.serialization.jackson.CborSerializable
import pekko.testkit.{ ImplicitSender, TestProbe }

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span

object ExternalShardAllocationSpecConfig
    extends MultiNodeClusterShardingConfig(additionalConfig = """
      pekko.cluster.sharding {
        retry-interval = 2000ms
        waiting-for-state-timeout = 2000ms
        rebalance-interval = 1s
      }
     """) {

  val first = role("first")
  val second = role("second")
  val third = role("third")
  val forth = role("forth")
}

class ExternalShardAllocationSpecMultiJvmNode1 extends ExternalShardAllocationSpec
class ExternalShardAllocationSpecMultiJvmNode2 extends ExternalShardAllocationSpec
class ExternalShardAllocationSpecMultiJvmNode3 extends ExternalShardAllocationSpec
class ExternalShardAllocationSpecMultiJvmNode4 extends ExternalShardAllocationSpec

object ExternalShardAllocationSpec {

  object GiveMeYourHome {
    case class Get(id: String) extends CborSerializable
    case class Home(address: Address) extends CborSerializable

    val extractEntityId: ShardRegion.ExtractEntityId = {
      case g @ Get(id) => (id, g)
    }

    // shard == id to make testing easier
    val extractShardId: ShardRegion.ExtractShardId = {
      case Get(id) => id
      case _       => throw new IllegalArgumentException()
    }
  }

  class GiveMeYourHome extends Actor with ActorLogging {

    val selfAddress = Cluster(context.system).selfAddress

    log.info("Started on {}", selfAddress)

    override def receive: Receive = {
      case Get(_) =>
        sender() ! Home(selfAddress)
    }
  }
}

abstract class ExternalShardAllocationSpec
    extends MultiNodeClusterShardingSpec(ExternalShardAllocationSpecConfig)
    with ImplicitSender
    with ScalaFutures {

  import ExternalShardAllocationSpec._
  import ExternalShardAllocationSpec.GiveMeYourHome._
  import ExternalShardAllocationSpecConfig._

  override implicit val patienceConfig: PatienceConfig = {
    import org.apache.pekko.testkit.TestDuration
    PatienceConfig(testKitSettings.DefaultTimeout.duration.dilated, Span(100, org.scalatest.time.Millis))
  }

  val typeName = "home"
  val initiallyOnForth = "on-forth"

  "External shard allocation" must {
    "form cluster" in {
      awaitClusterUp(first, second, third)
      enterBarrier("cluster-started")
    }

    lazy val shardRegion = startSharding(
      system,
      typeName = typeName,
      entityProps = Props[GiveMeYourHome](),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId,
      allocationStrategy = ExternalShardAllocationStrategy(system, typeName))

    "start cluster sharding" in {
      shardRegion
      enterBarrier("shard-region-started")
    }

    "default to allocating a shard to the local shard region" in {
      runOn(first, second, third) {
        shardRegion ! Get(myself.name)
        val actorLocation = expectMsgType[Home](20.seconds).address
        actorLocation shouldEqual Cluster(system).selfAddress
      }
      enterBarrier("local-message-sent")
    }

    "move shard via distributed data" in {
      val shardToSpecifyLocation = "cats"
      runOn(first) {
        ExternalShardAllocation(system)
          .clientFor(typeName)
          .updateShardLocation(shardToSpecifyLocation, Cluster(system).selfAddress)
          .futureValue
      }
      enterBarrier("shard-location-updated")

      runOn(second, third) {
        val probe = TestProbe()
        awaitAssert({
            shardRegion.tell(Get(shardToSpecifyLocation), probe.ref)
            probe.expectMsg(Home(address(first)))
          }, 10.seconds)
      }
      enterBarrier("shard-allocated-to-specific-node")
    }

    "allocate to a node that does not exist yet" in {
      val onForthShardId = "on-forth"
      val forthAddress = address(forth)
      runOn(second) {
        system.log.info("Allocating {} on {}", onForthShardId, forthAddress)
        ExternalShardAllocation(system).clientFor(typeName).updateShardLocations(Map(onForthShardId -> forthAddress))
      }
      enterBarrier("allocated-to-new-node")
      runOn(forth) {
        joinWithin(first, max = 10.seconds)
      }
      enterBarrier("forth-node-joined")
      runOn(first, second, third) {
        awaitAssert({
            shardRegion ! Get(initiallyOnForth)
            expectMsg(Home(address(forth)))
          }, 10.seconds)
      }
      enterBarrier("shard-allocated-to-forth")
    }

    "move allocation" in {
      runOn(third) {
        system.log.info("Moving shard from forth to first: {}", address(first))
        ExternalShardAllocation(system).clientFor(typeName).updateShardLocation(initiallyOnForth, address(first))
      }
      enterBarrier("shard-moved-from-forth-to-first")
      runOn(first, second, third, forth) {
        awaitAssert({
            shardRegion ! Get(initiallyOnForth)
            expectMsg(Home(address(first)))
          }, 10.seconds)
      }
      enterBarrier("finished")
    }
  }
}
