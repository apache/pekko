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
import pekko.actor.Props
import pekko.cluster.{ Cluster, MemberStatus }
import pekko.cluster.sharding.ShardRegion.StartEntity
import pekko.coordination.lease.TestLease
import pekko.coordination.lease.TestLeaseExt
import pekko.testkit.{ ImplicitSender, PekkoSpec, WithLogCapturing }
import pekko.testkit.TestActors.EchoActor

import com.typesafe.config.{ Config, ConfigFactory }

object ClusterShardingLeaseSpec {
  val config = ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.cluster.sharding {
       use-lease = "test-lease"
       lease-retry-interval = 200ms
       distributed-data.durable {
        keys = []
       }
       verbose-debug-logging = on
       fail-on-invalid-entity-state-transition = on
     }
    """).withFallback(TestLease.config)

  val persistenceConfig = ConfigFactory.parseString("""
      pekko.cluster.sharding {
        state-store-mode = persistence
        journal-plugin-id = "pekko.persistence.journal.inmem"
      }
    """)

  val ddataConfig = ConfigFactory.parseString("""
      pekko.cluster.sharding {
        state-store-mode = ddata
      }
    """)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: Int => (msg.toString, msg)
  }

  val numOfShards = 10

  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: Int         => (msg % numOfShards).toString
    case msg: StartEntity => (msg.entityId.toInt % numOfShards).toString
    case _                => throw new IllegalArgumentException()
  }
  case class LeaseFailed(msg: String) extends RuntimeException(msg) with NoStackTrace
}

class PersistenceClusterShardingLeaseSpec
    extends ClusterShardingLeaseSpec(ClusterShardingLeaseSpec.persistenceConfig, true)
class DDataClusterShardingLeaseSpec extends ClusterShardingLeaseSpec(ClusterShardingLeaseSpec.ddataConfig, true)

class ClusterShardingLeaseSpec(config: Config, rememberEntities: Boolean)
    extends PekkoSpec(config.withFallback(ClusterShardingLeaseSpec.config))
    with ImplicitSender
    with WithLogCapturing {
  import ClusterShardingLeaseSpec._

  def this() = this(ConfigFactory.empty(), false)

  val shortDuration = 200.millis
  val cluster = Cluster(system)
  val leaseOwner = cluster.selfMember.address.hostPort
  val testLeaseExt = TestLeaseExt(system)

  override protected def atStartup(): Unit = {
    cluster.join(cluster.selfAddress)
    awaitAssert {
      cluster.selfMember.status shouldEqual MemberStatus.Up
    }
    ClusterSharding(system).start(
      typeName = typeName,
      entityProps = Props[EchoActor](),
      settings = ClusterShardingSettings(system).withRememberEntities(rememberEntities),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId)
  }

  def region = ClusterSharding(system).shardRegion(typeName)

  val typeName = "echo"

  def leaseForShard(shardId: Int) = awaitAssert {
    testLeaseExt.getTestLease(leaseNameFor(shardId))
  }

  def leaseNameFor(shardId: Int, typeName: String = typeName): String =
    s"${system.name}-shard-$typeName-$shardId"

  "Cluster sharding with lease" should {
    "not start until lease is acquired" in {
      region ! 1
      expectNoMessage(shortDuration)
      val testLease = leaseForShard(1)
      testLease.initialPromise.complete(Success(true))
      expectMsg(1)
    }
    "retry if initial acquire is false" in {
      region ! 2
      expectNoMessage(shortDuration)
      val testLease = leaseForShard(2)
      testLease.initialPromise.complete(Success(false))
      expectNoMessage(shortDuration)
      testLease.setNextAcquireResult(Future.successful(true))
      expectMsg(2)
    }
    "retry if initial acquire fails" in {
      region ! 3
      expectNoMessage(shortDuration)
      val testLease = leaseForShard(3)
      testLease.initialPromise.failure(LeaseFailed("oh no"))
      expectNoMessage(shortDuration)
      testLease.setNextAcquireResult(Future.successful(true))
      expectMsg(3)
    }
    "recover if lease lost" in {
      region ! 4
      expectNoMessage(shortDuration)
      val testLease = leaseForShard(4)
      testLease.initialPromise.complete(Success(true))
      expectMsg(4)
      testLease.getCurrentCallback()(Option(LeaseFailed("oh dear")))
      awaitAssert({
          region ! 4
          expectMsg(4)
        }, max = 10.seconds)
    }
  }
}
