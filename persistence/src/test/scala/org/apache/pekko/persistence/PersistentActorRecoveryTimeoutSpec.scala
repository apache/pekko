/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, ActorRef, Props }
import pekko.actor.Status.Failure
import pekko.persistence.journal.SteppingInmemJournal
import pekko.testkit.{ ImplicitSender, PekkoSpec, TestDuration, TestProbe }

import com.typesafe.config.ConfigFactory

object PersistentActorRecoveryTimeoutSpec {
  val journalId = "persistent-actor-recovery-timeout-spec"
  val receiveTimeoutJournalId = "persistent-actor-recovery-timeout-spec-receive-timeout"
  val receiveTimeoutJournalPluginId = "pekko.persistence.journal.stepping-inmem-receive-timeout"

  def config =
    SteppingInmemJournal
      .config(PersistentActorRecoveryTimeoutSpec.journalId)
      .withFallback(ConfigFactory.parseString(s"""
          |pekko.persistence.journal.stepping-inmem.recovery-event-timeout=3s
          |$receiveTimeoutJournalPluginId.class=${classOf[SteppingInmemJournal].getName}
          |$receiveTimeoutJournalPluginId.instance-id="$receiveTimeoutJournalId"
          |$receiveTimeoutJournalPluginId.recovery-event-timeout=30s
        """.stripMargin))
      .withFallback(PersistenceSpec.config("stepping-inmem", "PersistentActorRecoveryTimeoutSpec"))

  class TestActor(probe: ActorRef) extends NamedPersistentActor("recovery-timeout-actor") {
    override def receiveRecover: Receive = Actor.emptyBehavior

    override def receiveCommand: Receive = {
      case x =>
        persist(x) { _ =>
          sender() ! x
        }
    }

    override protected def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      probe ! Failure(cause)
    }
  }

  class TestReceiveTimeoutActor(receiveTimeout: FiniteDuration, probe: ActorRef)
      extends NamedPersistentActor("recovery-timeout-actor-2")
      with ActorLogging {

    override def journalPluginId: String = receiveTimeoutJournalPluginId

    override def preStart(): Unit = {
      context.setReceiveTimeout(receiveTimeout)
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted => probe ! context.receiveTimeout
      case _                 => // we don't care
    }

    override def receiveCommand: Receive = {
      case x =>
        persist(x) { _ =>
          sender() ! x
        }
    }

    override protected def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error(cause, "Recovery of TestReceiveTimeoutActor failed")
      probe ! Failure(cause)
    }
  }

}

class PersistentActorRecoveryTimeoutSpec
    extends PekkoSpec(PersistentActorRecoveryTimeoutSpec.config)
    with ImplicitSender {

  import PersistentActorRecoveryTimeoutSpec.{ journalId, receiveTimeoutJournalId }

  "The recovery timeout" should {

    "fail recovery if timeout is not met when recovering" in {
      val probe = TestProbe()
      val persisting = system.actorOf(Props(classOf[PersistentActorRecoveryTimeoutSpec.TestActor], probe.ref))

      awaitAssert(SteppingInmemJournal.getRef(journalId), 3.seconds)
      val journal = SteppingInmemJournal.getRef(journalId)

      // initial read highest
      SteppingInmemJournal.step(journal)

      persisting ! "A"
      SteppingInmemJournal.step(journal)
      expectMsg("A")

      watch(persisting)
      system.stop(persisting)
      expectTerminated(persisting)

      // now replay, but don't give the journal any tokens to replay events
      // so that we cause the timeout to trigger
      val replaying = system.actorOf(Props(classOf[PersistentActorRecoveryTimeoutSpec.TestActor], probe.ref))
      watch(replaying)

      // initial read highest
      SteppingInmemJournal.step(journal)

      // recovery-event-timeout is set to 3s; allow extra headroom for CI
      probe.expectMsgType[Failure](10.seconds).cause shouldBe a[RecoveryTimedOut]
      expectTerminated(replaying)

      // avoid having it stuck in the next test from the
      // last read request above
      SteppingInmemJournal.step(journal)
    }

    "should not interfere with receive timeouts" in {
      val timeout = 42.days

      val probe = TestProbe()
      val persisting =
        system.actorOf(Props(classOf[PersistentActorRecoveryTimeoutSpec.TestReceiveTimeoutActor], timeout, probe.ref))

      awaitAssert(SteppingInmemJournal.getRef(receiveTimeoutJournalId), 3.seconds)
      val journal = SteppingInmemJournal.getRef(receiveTimeoutJournalId)

      // initial read highest
      SteppingInmemJournal.step(journal)
      probe.expectMsg(timeout)

      persisting ! "A"
      SteppingInmemJournal.step(journal)
      expectMsg("A")

      watch(persisting)
      system.stop(persisting)
      expectTerminated(persisting)

      // now replay and verify that recovery keeps the actor's receive timeout
      system.actorOf(Props(classOf[PersistentActorRecoveryTimeoutSpec.TestReceiveTimeoutActor], timeout, probe.ref))

      // Release both recovery journal operations up front. Waiting for the second stepped
      // operation can race with the recovery timeout under heavy CI load.
      journal ! SteppingInmemJournal.Token
      journal ! SteppingInmemJournal.Token

      // we should get initial receive timeout back from actor when replay completes
      probe.expectMsg(30.seconds.dilated, timeout)

    }

  }

}
