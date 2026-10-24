/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.singleton

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Success

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.ActorRef
import pekko.actor.ExtendedActorSystem
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.ClusterEvent.MemberDowned
import pekko.cluster.MemberStatus
import pekko.coordination.lease.TestLease
import pekko.coordination.lease.TestLeaseExt
import pekko.event.Logging
import pekko.testkit.EventFilter
import pekko.testkit.PekkoSpec
import pekko.testkit.TestException
import pekko.testkit.TestProbe

import com.typesafe.config.ConfigFactory

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
     pekko.remote.artery.canonical.port = 0

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

  private def startedSingleton(restartable: Boolean = false): (ActorRef, TestLease, TestProbe) = {
    val lifecycleProbe = TestProbe()
    val settings = nextSettings()
    val singletonProps = Props(new ImportantSingleton(lifecycleProbe.ref))
    val props = ClusterSingletonManager.props(singletonProps, PoisonPill, settings)
    val managerProps =
      if (restartable)
        Props(new ClusterSingletonManager(singletonProps, PoisonPill, settings) {
          override protected[pekko] def aroundReceive(receive: Actor.Receive, msg: Any): Unit = msg match {
            case "crash-manager" => throw TestException("restart singleton manager")
            case _               => super.aroundReceive(receive, msg)
          }
        }).withDispatcher(props.dispatcher)
      else props
    val manager = system.actorOf(managerProps, settings.singletonName)
    val testLease = awaitAssert {
      testLeaseExt.getTestLease(leaseNameFor(settings))
    }
    testLease.probe.expectMsg(AcquireReq(leaseOwner))
    testLease.initialPromise.success(true)
    lifecycleProbe.expectMsg("preStart")
    (manager, testLease, lifecycleProbe)
  }

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

    "use custom lease name" in {
      val probe = TestProbe()
      val defaultSettings = nextSettings()
      val customLeaseName = s"custom-lease-${defaultSettings.singletonName}"
      val settings =
        defaultSettings.withLeaseSettings(defaultSettings.leaseSettings.get.withLeaseName(customLeaseName))
      settings.leaseSettings.get.leaseName shouldBe customLeaseName
      system.actorOf(
        ClusterSingletonManager.props(Props(new ImportantSingleton(probe.ref)), PoisonPill, settings),
        settings.singletonName)
      val testLease = awaitAssert {
        testLeaseExt.getTestLease(customLeaseName)
      } // allow singleton manager to create the lease
      probe.expectNoMessage(shortDuration)
      testLease.initialPromise.complete(Success(true))
      probe.expectMsg("preStart")
      intercept[IllegalStateException] {
        testLeaseExt.getTestLease(leaseNameFor(settings))
      }
    }

    "log error when lease release fails leaving oldest" in {
      val (manager, testLease, lifecycleProbe) = startedSingleton()
      testLease.setNextAcquireResult(Promise[Boolean]().future)
      testLease.setNextReleaseResult(Future.failed(TestException("no release for you")))
      EventFilter[TestException](pattern = ".*Failed to release lease.*", occurrences = 1).intercept {
        testLease.getCurrentCallback()(Some(TestException("lease lost")))
        testLease.probe.expectMsg(ReleaseReq(leaseOwner))
      }
      lifecycleProbe.expectMsg("postStop")
      lifecycleProbe.watch(manager)
      system.stop(manager)
      lifecycleProbe.expectTerminated(manager)
    }

    "log error when lease release throws synchronously" in {
      val (manager, testLease, lifecycleProbe) = startedSingleton()
      testLease.setNextAcquireResult(Promise[Boolean]().future)
      testLease.setNextReleaseResult(throw TestException("synchronous release failure"))
      EventFilter[TestException](pattern = ".*Failed to release lease.*", occurrences = 1).intercept {
        testLease.getCurrentCallback()(None)
        testLease.probe.expectMsg(ReleaseReq(leaseOwner))
      }
      lifecycleProbe.expectMsg("postStop")
      testLease.probe.expectMsg(AcquireReq(leaseOwner))
      lifecycleProbe.watch(manager)
      system.stop(manager)
      lifecycleProbe.expectTerminated(manager)
    }

    "log a failed release with the manager logger after the manager has stopped" in {
      val (manager, testLease, lifecycleProbe) = startedSingleton()
      val terminationProbe = TestProbe()
      val releaseResult = Promise[Boolean]()
      testLease.setNextReleaseResult(releaseResult.future)
      terminationProbe.watch(manager)
      manager ! MemberDowned(cluster.selfMember.copy(MemberStatus.Down))
      testLease.probe.expectMsg(ReleaseReq(leaseOwner))
      lifecycleProbe.expectMsg("postStop")
      terminationProbe.expectTerminated(manager)
      val failure = TestException("late release failure")
      EventFilter.custom({
          case error: Logging.Error =>
            (error.cause eq failure) && error.logClass == classOf[ClusterSingletonManager] &&
            error.message.toString.startsWith("Failed to release lease")
        }, occurrences = 1).intercept {
        releaseResult.failure(failure)
      }
      // The transition already released the lease; termination must not release it again.
      testLease.probe.expectNoMessage(shortDuration)
    }

    "log an unsuccessful release after the manager has stopped" in {
      val (manager, testLease, lifecycleProbe) = startedSingleton()
      val terminationProbe = TestProbe()
      val releaseResult = Promise[Boolean]()
      testLease.setNextReleaseResult(releaseResult.future)
      terminationProbe.watch(manager)
      manager ! MemberDowned(cluster.selfMember.copy(MemberStatus.Down))
      testLease.probe.expectMsg(ReleaseReq(leaseOwner))
      lifecycleProbe.expectMsg("postStop")
      terminationProbe.expectTerminated(manager)
      EventFilter.error(pattern = ".*Failed to release lease.*", occurrences = 1).intercept {
        releaseResult.success(false)
      }
    }

    "release the lease once when stopped directly as oldest" in {
      val (manager, testLease, lifecycleProbe) = startedSingleton()
      val terminationProbe = TestProbe()
      terminationProbe.watch(manager)
      system.stop(manager)
      lifecycleProbe.expectMsg("postStop")
      terminationProbe.expectTerminated(manager)
      testLease.probe.expectMsg(ReleaseReq(leaseOwner))
      testLease.probe.expectNoMessage(shortDuration)
    }

    "retain the lease across an exceptional restart and release it on the final stop" in {
      val (manager, testLease, lifecycleProbe) = startedSingleton(restartable = true)
      EventFilter[TestException](message = "restart singleton manager", occurrences = 1).intercept {
        manager ! "crash-manager"
        lifecycleProbe.expectMsg("postStop")
        // A release here would precede the new acquire and could revoke its ownership later.
        testLease.probe.expectMsg(AcquireReq(leaseOwner))
        lifecycleProbe.expectMsg("preStart")
      }
      // The restarted singleton sent preStart, so reply to that exact actor incarnation.
      lifecycleProbe.reply("still active")
      lifecycleProbe.expectMsg("still active")
      val terminationProbe = TestProbe()
      terminationProbe.watch(manager)
      system.stop(manager)
      lifecycleProbe.expectMsg("postStop")
      terminationProbe.expectTerminated(manager)
      testLease.probe.expectMsg(ReleaseReq(leaseOwner))
      testLease.probe.expectNoMessage(shortDuration)
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
