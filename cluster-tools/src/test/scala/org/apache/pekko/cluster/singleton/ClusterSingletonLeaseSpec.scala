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

package org.apache.pekko.cluster.singleton

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Success

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.ActorRef
import pekko.actor.ExtendedActorSystem
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.coordination.lease.TestLease
import pekko.coordination.lease.TestLeaseExt
import pekko.testkit.PekkoSpec
import pekko.testkit.TestException
import pekko.testkit.TestProbe

class ImportantSingleton(lifeCycleProbe: ActorRef) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    log.info("Important Singleton Starting")
    lifeCycleProbe ! "preStart"
  }

  override def postStop(): Unit = {
    log.info("Important Singleton Stopping")
    lifeCycleProbe ! "postStop"
  }

  override def receive: Receive = {
    case msg =>
      sender() ! msg
  }
}

class ClusterSingletonLeaseSpec extends PekkoSpec(ConfigFactory.parseString("""
     pekko.loglevel = INFO
     pekko.actor.provider = cluster

     pekko.cluster.singleton {
       use-lease = "test-lease"
       lease-retry-interval = 2000ms
     }
  """).withFallback(TestLease.config)) {
  import TestLease.{ AcquireReq, ReleaseReq }

  val cluster = Cluster(system)
  val testLeaseExt = TestLeaseExt(system)

  override protected def atStartup(): Unit = {
    cluster.join(cluster.selfAddress)
    awaitAssert {
      cluster.selfMember.status shouldEqual MemberStatus.Up
    }
  }

  def extSystem: ExtendedActorSystem = system.asInstanceOf[ExtendedActorSystem]

  val counter = new AtomicInteger()

  def nextName() = s"important-${counter.getAndIncrement()}"

  val shortDuration = 50.millis

  val leaseOwner = cluster.selfMember.address.hostPort

  def nextSettings() = ClusterSingletonManagerSettings(system).withSingletonName(nextName())

  def leaseNameFor(settings: ClusterSingletonManagerSettings): String =
    s"ClusterSingletonLeaseSpec-singleton-pekko://ClusterSingletonLeaseSpec/user/${settings.singletonName}"

  "A singleton with lease" should {

    "not start until lease is available" in {
      val probe = TestProbe()
      val settings = nextSettings()
      system.actorOf(
        ClusterSingletonManager.props(Props(new ImportantSingleton(probe.ref)), PoisonPill, settings),
        settings.singletonName)
      val testLease = awaitAssert {
        testLeaseExt.getTestLease(leaseNameFor(settings))
      } // allow singleton manager to create the lease
      probe.expectNoMessage(shortDuration)
      testLease.initialPromise.complete(Success(true))
      probe.expectMsg("preStart")
    }

    "do not start if lease acquire returns false" in {
      val probe = TestProbe()
      val settings = nextSettings()
      system.actorOf(
        ClusterSingletonManager.props(Props(new ImportantSingleton(probe.ref)), PoisonPill, settings),
        settings.singletonName)
      val testLease = awaitAssert {
        testLeaseExt.getTestLease(leaseNameFor(settings))
      } // allow singleton manager to create the lease
      probe.expectNoMessage(shortDuration)
      testLease.initialPromise.complete(Success(false))
      probe.expectNoMessage(shortDuration)
    }

    "retry trying to get lease if acquire returns false" in {
      val singletonProbe = TestProbe()
      val settings = nextSettings()
      system.actorOf(
        ClusterSingletonManager.props(Props(new ImportantSingleton(singletonProbe.ref)), PoisonPill, settings),
        settings.singletonName)
      val testLease = awaitAssert {
        testLeaseExt.getTestLease(leaseNameFor(settings))
      } // allow singleton manager to create the lease
      testLease.probe.expectMsg(AcquireReq(leaseOwner))
      singletonProbe.expectNoMessage(shortDuration)
      val nextResponse = Promise[Boolean]()
      testLease.setNextAcquireResult(nextResponse.future)
      testLease.initialPromise.complete(Success(false))
      testLease.probe.expectMsg(AcquireReq(leaseOwner))
      singletonProbe.expectNoMessage(shortDuration)
      nextResponse.complete(Success(true))
      singletonProbe.expectMsg("preStart")
    }

    "do not start if lease acquire fails" in {
      val probe = TestProbe()
      val settings = nextSettings()
      system.actorOf(
        ClusterSingletonManager.props(Props(new ImportantSingleton(probe.ref)), PoisonPill, settings),
        settings.singletonName)
      val testLease = awaitAssert {
        testLeaseExt.getTestLease(leaseNameFor(settings))
      } // allow singleton manager to create the lease
      probe.expectNoMessage(shortDuration)
      testLease.initialPromise.failure(TestException("no lease for you"))
      probe.expectNoMessage(shortDuration)
    }

    "retry trying to get lease if acquire returns fails" in {
      val singletonProbe = TestProbe()
      val settings = nextSettings()
      system.actorOf(
        ClusterSingletonManager.props(Props(new ImportantSingleton(singletonProbe.ref)), PoisonPill, settings),
        settings.singletonName)
      val testLease = awaitAssert {
        testLeaseExt.getTestLease(leaseNameFor(settings))
      } // allow singleton manager to create the lease
      testLease.probe.expectMsg(AcquireReq(leaseOwner))
      singletonProbe.expectNoMessage(shortDuration)
      val nextResponse = Promise[Boolean]()
      testLease.setNextAcquireResult(nextResponse.future)
      testLease.initialPromise.failure(TestException("no lease for you"))
      testLease.probe.expectMsg(AcquireReq(leaseOwner))
      singletonProbe.expectNoMessage(shortDuration)
      nextResponse.complete(Success(true))
      singletonProbe.expectMsg("preStart")
    }

    "stop singleton if the lease fails periodic check" in {
      val lifecycleProbe = TestProbe()
      val settings = nextSettings()
      system.actorOf(
        ClusterSingletonManager.props(Props(new ImportantSingleton(lifecycleProbe.ref)), PoisonPill, settings),
        settings.singletonName)
      val testLease = awaitAssert {
        testLeaseExt.getTestLease(leaseNameFor(settings))
      }
      testLease.probe.expectMsg(AcquireReq(leaseOwner))
      testLease.initialPromise.complete(Success(true))
      lifecycleProbe.expectMsg("preStart")
      val callback = testLease.getCurrentCallback()
      callback(None)
      lifecycleProbe.expectMsg("postStop")
      testLease.probe.expectMsg(ReleaseReq(leaseOwner))

      // should try and reacquire lease
      testLease.probe.expectMsg(AcquireReq(leaseOwner))
      lifecycleProbe.expectMsg("preStart")
    }

    "release lease when leaving oldest" in {
      val singletonProbe = TestProbe()
      val settings = nextSettings()
      system.actorOf(
        ClusterSingletonManager.props(Props(new ImportantSingleton(singletonProbe.ref)), PoisonPill, settings),
        settings.singletonName)
      val testLease = awaitAssert {
        testLeaseExt.getTestLease(leaseNameFor(settings))
      } // allow singleton manager to create the lease
      singletonProbe.expectNoMessage(shortDuration)
      testLease.probe.expectMsg(AcquireReq(leaseOwner))
      testLease.initialPromise.complete(Success(true))
      singletonProbe.expectMsg("preStart")
      cluster.leave(cluster.selfAddress)
      testLease.probe.expectMsg(ReleaseReq(leaseOwner))
    }
  }
}
