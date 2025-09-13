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

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.cluster.sharding.ShardRegion.ShardId
import pekko.coordination.lease.{ LeaseUsageSettings, TestLeaseExt }
import pekko.testkit.EventFilter
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe
import pekko.testkit.WithLogCapturing

// FIXME this looks like it is the same test as ClusterShardingLeaseSpec is there any difference?
object ShardWithLeaseSpec {
  val config =
    """
      pekko.loglevel = DEBUG
      pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
      pekko.actor.provider = "cluster"
      pekko.remote.classic.netty.tcp.port = 0
      pekko.remote.artery.canonical.port = 0
      test-lease {
          lease-class = org.apache.pekko.coordination.lease.TestLease
          heartbeat-interval = 1s
          heartbeat-timeout = 120s
          lease-operation-timeout = 3s
      }
      pekko.cluster.sharding.verbose-debug-logging = on
      pekko.cluster.sharding.fail-on-invalid-entity-state-transition = on
    """

  class EntityActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case msg =>
        log.info("Msg {}", msg)
        sender() ! s"ack $msg"
    }
  }

  val numberOfShards = 5

  case class EntityEnvelope(entityId: Int, msg: Any)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) => (id.toString, payload)
    case _                           => throw new IllegalArgumentException()
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) => (id % numberOfShards).toString
    case _                     => throw new IllegalArgumentException()
  }

  case class BadLease(msg: String) extends RuntimeException(msg) with NoStackTrace
}

class ShardWithLeaseSpec extends PekkoSpec(ShardWithLeaseSpec.config) with WithLogCapturing {

  import ShardWithLeaseSpec._

  val shortDuration = 100.millis
  val testLeaseExt = TestLeaseExt(system)

  override def atStartup(): Unit = {
    // Form a one node cluster
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
    awaitAssert(cluster.readView.members.count(_.status == MemberStatus.Up) should ===(1))
  }

  "Lease handling in sharding" must {
    "not initialize the shard until the lease is acquired" in new Setup {
      val probe = TestProbe()
      sharding.tell(EntityEnvelope(1, "hello"), probe.ref)
      probe.expectNoMessage(shortDuration)
      leaseFor("1").initialPromise.complete(Success(true))
      probe.expectMsg("ack hello")
    }

    "retry if lease acquire returns false" in new Setup {
      val probe = TestProbe()
      val lease =
        EventFilter.error(start = s"$typeName: Failed to get lease for shard id [1]", occurrences = 1).intercept {
          sharding.tell(EntityEnvelope(1, "hello"), probe.ref)
          val lease = leaseFor("1")
          lease.initialPromise.complete(Success(false))
          probe.expectNoMessage(shortDuration)
          lease
        }

      lease.setNextAcquireResult(Future.successful(true))
      probe.expectMsg("ack hello")
    }

    "retry if the lease acquire fails" in new Setup {
      val probe = TestProbe()
      val lease =
        EventFilter.error(start = s"$typeName: Failed to get lease for shard id [1]", occurrences = 1).intercept {
          sharding.tell(EntityEnvelope(1, "hello"), probe.ref)
          val lease = leaseFor("1")
          lease.initialPromise.failure(BadLease("no lease for you"))
          probe.expectNoMessage(shortDuration)
          lease
        }
      lease.setNextAcquireResult(Future.successful(true))
      probe.expectMsg("ack hello")
    }

    "shutdown if lease is lost" in new Setup {
      val probe = TestProbe()
      sharding.tell(EntityEnvelope(1, "hello"), probe.ref)
      val lease = leaseFor("1")
      lease.initialPromise.complete(Success(true))
      probe.expectMsg("ack hello")

      EventFilter
        .error(
          start =
            s"$typeName: Shard id [1] lease lost, stopping shard and killing [1] entities. Reason for losing lease: ${classOf[
                BadLease].getName}: bye bye lease",
          occurrences = 1)
        .intercept {
          lease.getCurrentCallback().apply(Some(BadLease("bye bye lease")))
        }

      lease.setNextAcquireResult(Future.successful(false))
      sharding.tell(EntityEnvelope(1, "hello"), probe.ref)
      probe.expectNoMessage(shortDuration)
    }
  }

  var typeIdx = 0

  trait Setup {
    val settings = ClusterShardingSettings(system).withLeaseSettings(new LeaseUsageSettings("test-lease", 2.seconds))

    // unique type name for each test
    val typeName = {
      typeIdx += 1
      s"type$typeIdx"
    }

    val sharding =
      ClusterSharding(system).start(typeName, Props(new EntityActor()), settings, extractEntityId, extractShardId)

    def leaseFor(shardId: ShardId) = awaitAssert {
      val leaseName = s"${system.name}-shard-$typeName-$shardId"
      testLeaseExt.getTestLease(leaseName)
    }
  }

}
