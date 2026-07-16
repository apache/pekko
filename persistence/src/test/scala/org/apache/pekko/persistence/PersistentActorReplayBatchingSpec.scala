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

package org.apache.pekko.persistence

import scala.collection.immutable
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._
import scala.util.Try

import org.apache.pekko.actor.{ ActorIdentity, ActorRef, Identify, Props }
import org.apache.pekko.persistence.journal.{ AsyncWriteJournal, SteppingInmemJournal }
import org.apache.pekko.testkit.{ PekkoSpec, TestProbe }

import com.typesafe.config.ConfigFactory

object RestartingReplayJournal {
  case object Crash
  final class CrashException extends RuntimeException("restart the test journal")

  final case class Control(incarnations: Vector[Future[Unit]], reads: Vector[Future[Unit]])

  private var incarnationStarted = Vector.empty[Promise[Unit]]
  private var readStarted = Vector.empty[Promise[Unit]]
  private var readResults = Vector.empty[Promise[Long]]
  private var incarnation = 0
  private var read = 0

  def prepare(): Control = synchronized {
    incarnationStarted = Vector.fill(2)(Promise[Unit]())
    readStarted = Vector.fill(2)(Promise[Unit]())
    readResults = Vector.fill(2)(Promise[Long]())
    incarnation = 0
    read = 0
    Control(incarnationStarted.map(_.future), readStarted.map(_.future))
  }

  private def onStart(): Unit = synchronized {
    incarnationStarted(incarnation).success(())
    incarnation += 1
  }

  private def nextRead(): Future[Long] = synchronized {
    val current = read
    read += 1
    readStarted(current).success(())
    readResults(current).future
  }

  def completeRead(read: Int, highestSequenceNr: Long): Unit = synchronized {
    readResults(read).success(highestSequenceNr)
  }
}

final class RestartingReplayJournal extends AsyncWriteJournal {
  import RestartingReplayJournal._

  override def preStart(): Unit = {
    super.preStart()
    onStart()
  }

  override def receivePluginInternal: Receive = {
    case Crash => throw new CrashException
  }

  override def asyncWriteMessages(
      messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = Future.successful(Nil)

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    Future.successful(())

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    nextRead()

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
      recoveryCallback: PersistentRepr => Unit): Future[Unit] = Future.successful(())
}

object PersistentActorReplayBatchingSpec {
  val JournalId = "persistent-actor-replay-batching-spec"
  val RestartingJournalId = "restarting-replay-journal"

  final case class PersistAll(events: Vector[String])
  final case class Persisted(event: String)
  final case class DeleteTo(toSequenceNr: Long)
  final case class Deleted(toSequenceNr: Long)
  final case class Replayed(event: String, eventSender: ActorRef)
  case object RecoveryFinished

  val config =
    SteppingInmemJournal
      .config(JournalId)
      .withFallback(ConfigFactory.parseString(s"""
          pekko.persistence.journal.stepping-inmem {
            replay-batch-size = 2
            replay-filter.mode = off
          }
          $RestartingJournalId = $${pekko.persistence.journal-plugin-fallback}
          $RestartingJournalId {
            class = "org.apache.pekko.persistence.RestartingReplayJournal"
            replay-batch-size = 2
            replay-filter.mode = off
          }
        """))
      .withFallback(PersistenceSpec.config("stepping-inmem", "PersistentActorReplayBatchingSpec"))
      .withFallback(ConfigFactory.defaultReference())
      .resolve()

  final class TestActor(override val persistenceId: String, replayMax: Long, probe: ActorRef) extends PersistentActor {
    override def recovery: Recovery = Recovery(replayMax = replayMax)

    override def receiveRecover: Receive = {
      case event: String     => probe ! Replayed(event, sender())
      case RecoveryCompleted => probe ! RecoveryFinished
    }

    override def receiveCommand: Receive = {
      case PersistAll(events) =>
        val replyTo = sender()
        persistAll(events) { event =>
          replyTo ! Persisted(event)
        }
      case DeleteTo(toSequenceNr)              => deleteMessages(toSequenceNr)
      case DeleteMessagesSuccess(toSequenceNr) => probe ! Deleted(toSequenceNr)
    }
  }
}

class PersistentActorReplayBatchingSpec extends PekkoSpec(PersistentActorReplayBatchingSpec.config) {
  import PersistentActorReplayBatchingSpec._
  import JournalProtocol._

  private def nextBatchedResponse(receiver: TestProbe, actorInstanceId: Int): JournalProtocol.Response = {
    val wrapped = receiver.expectMsgType[ReplayBatchResponse]
    wrapped.actorInstanceId shouldBe actorInstanceId
    receiver.lastSender shouldBe system.deadLetters
    wrapped.response
  }

  private def expectReplayed(receiver: TestProbe, actorInstanceId: Int, payload: String): Unit =
    nextBatchedResponse(receiver, actorInstanceId) match {
      case ReplayedMessage(persistent) => persistent.payload shouldBe payload
      case other                       => fail(s"Expected ReplayedMessage, got [$other]")
    }

  private def expectBatchReady(receiver: TestProbe, actorInstanceId: Int): ReplayBatchReady =
    nextBatchedResponse(receiver, actorInstanceId) match {
      case ready: ReplayBatchReady => ready
      case other                   => fail(s"Expected ReplayBatchReady, got [$other]")
    }

  "A PersistentActor recovery" should {
    "acknowledge each bounded replay batch before the journal produces the next batch" in {
      val probe = TestProbe()
      val persistenceId = "bounded-replay"
      val events = Vector("a", "b", "c", "d", "e")

      val persisting = system.actorOf(Props(classOf[TestActor], persistenceId, Long.MaxValue, probe.ref))
      awaitAssert(SteppingInmemJournal.getRef(JournalId), 3.seconds)
      val journal = SteppingInmemJournal.getRef(JournalId)

      SteppingInmemJournal.step(journal)
      probe.expectMsg(RecoveryFinished)

      persisting.tell(PersistAll(events), probe.ref)
      SteppingInmemJournal.step(journal)
      events.foreach(event => probe.expectMsg(Persisted(event)))

      watch(persisting)
      system.stop(persisting)
      expectTerminated(persisting)

      system.actorOf(Props(classOf[TestActor], persistenceId, Long.MaxValue, probe.ref))
      SteppingInmemJournal.step(journal) // read highest sequence number

      SteppingInmemJournal.step(journal) // replay 1-2
      probe.expectMsg(Replayed("a", system.deadLetters))
      probe.expectMsg(Replayed("b", system.deadLetters))
      probe.expectNoMessage(100.millis)

      SteppingInmemJournal.step(journal) // replay 3-4
      probe.expectMsg(Replayed("c", system.deadLetters))
      probe.expectMsg(Replayed("d", system.deadLetters))
      probe.expectNoMessage(100.millis)

      SteppingInmemJournal.step(journal) // replay 5 and complete
      probe.expectMsg(Replayed("e", system.deadLetters))
      probe.expectMsg(RecoveryFinished)
    }

    "preserve the total replayMax limit across batches" in {
      val probe = TestProbe()
      val persistenceId = "bounded-replay-max"
      val events = Vector("a", "b", "c", "d", "e")

      val persisting = system.actorOf(Props(classOf[TestActor], persistenceId, Long.MaxValue, probe.ref))
      val journal = SteppingInmemJournal.getRef(JournalId)
      SteppingInmemJournal.step(journal)
      probe.expectMsg(RecoveryFinished)

      persisting.tell(PersistAll(events), probe.ref)
      SteppingInmemJournal.step(journal)
      events.foreach(event => probe.expectMsg(Persisted(event)))
      watch(persisting)
      system.stop(persisting)
      expectTerminated(persisting)

      system.actorOf(Props(classOf[TestActor], persistenceId, 3L, probe.ref))
      SteppingInmemJournal.step(journal) // read highest sequence number

      SteppingInmemJournal.step(journal) // replay 1-2
      probe.expectMsg(Replayed("a", system.deadLetters))
      probe.expectMsg(Replayed("b", system.deadLetters))
      probe.expectNoMessage(100.millis)

      SteppingInmemJournal.step(journal) // replay only 3 because replayMax is now exhausted
      probe.expectMsg(Replayed("c", system.deadLetters))
      probe.expectMsg(RecoveryFinished)
      probe.expectNoMessage(100.millis)
    }

    "advance across empty sequence number ranges caused by deleted events" in {
      val probe = TestProbe()
      val persistenceId = "bounded-replay-deleted-events"
      val events = Vector("a", "b", "c", "d", "e")

      val persisting = system.actorOf(Props(classOf[TestActor], persistenceId, Long.MaxValue, probe.ref))
      val journal = SteppingInmemJournal.getRef(JournalId)
      SteppingInmemJournal.step(journal)
      probe.expectMsg(RecoveryFinished)

      persisting.tell(PersistAll(events), probe.ref)
      SteppingInmemJournal.step(journal)
      events.foreach(event => probe.expectMsg(Persisted(event)))

      persisting ! DeleteTo(3L)
      SteppingInmemJournal.step(journal)
      probe.expectMsg(Deleted(3L))
      watch(persisting)
      system.stop(persisting)
      expectTerminated(persisting)

      system.actorOf(Props(classOf[TestActor], persistenceId, Long.MaxValue, probe.ref))
      SteppingInmemJournal.step(journal) // read highest sequence number

      SteppingInmemJournal.step(journal) // replay empty range 1-2
      probe.expectNoMessage(100.millis)

      SteppingInmemJournal.step(journal) // replay 3-4, with only 4 remaining
      probe.expectMsg(Replayed("d", system.deadLetters))
      probe.expectNoMessage(100.millis)

      SteppingInmemJournal.step(journal) // replay 5 and complete
      probe.expectMsg(Replayed("e", system.deadLetters))
      probe.expectMsg(RecoveryFinished)
    }

    "prevent the journal from producing another batch before acknowledgement" in {
      val probe = TestProbe()
      val persistenceId = "withheld-batch-ack"
      val events = Vector("a", "b", "c", "d", "e")

      val persisting = system.actorOf(Props(classOf[TestActor], persistenceId, Long.MaxValue, probe.ref))
      val journal = SteppingInmemJournal.getRef(JournalId)
      SteppingInmemJournal.step(journal)
      probe.expectMsg(RecoveryFinished)
      persisting.tell(PersistAll(events), probe.ref)
      SteppingInmemJournal.step(journal)
      events.foreach(event => probe.expectMsg(Persisted(event)))
      watch(persisting)
      system.stop(persisting)
      expectTerminated(persisting)

      val receiver = TestProbe()
      val actorInstanceId = 17
      journal.tell(ReplayMessagesWithBatching(actorInstanceId), receiver.ref)
      journal.tell(ReplayMessages(1L, Long.MaxValue, Long.MaxValue, persistenceId, receiver.ref), receiver.ref)
      SteppingInmemJournal.step(journal) // read highest sequence number
      SteppingInmemJournal.step(journal) // replay 1-2

      expectReplayed(receiver, actorInstanceId, "a")
      expectReplayed(receiver, actorInstanceId, "b")
      val firstBatch = expectBatchReady(receiver, actorInstanceId)

      // Make a replay token available, but withhold the acknowledgement. No second replay call may start.
      val tokenProbe = TestProbe()
      journal.tell(SteppingInmemJournal.Token, tokenProbe.ref)
      receiver.expectNoMessage(100.millis)
      tokenProbe.expectNoMessage(100.millis)

      journal.tell(ReplayBatchAck(firstBatch.replayId), receiver.ref)
      tokenProbe.expectMsg(SteppingInmemJournal.TokenConsumed)
      expectReplayed(receiver, actorInstanceId, "c")
      expectReplayed(receiver, actorInstanceId, "d")
      val secondBatch = expectBatchReady(receiver, actorInstanceId)

      journal.tell(ReplayBatchAck(secondBatch.replayId), receiver.ref)
      SteppingInmemJournal.step(journal)
      expectReplayed(receiver, actorInstanceId, "e")
      nextBatchedResponse(receiver, actorInstanceId) shouldBe RecoverySuccess(5L)
    }

    "discard an unpaired batching request when its requester terminates" in {
      val probe = TestProbe()
      val persistenceId = "terminated-batching-requester"
      val journal = SteppingInmemJournal.getRef(JournalId)

      val persisting = system.actorOf(Props(classOf[TestActor], persistenceId, Long.MaxValue, probe.ref))
      SteppingInmemJournal.step(journal)
      probe.expectMsg(RecoveryFinished)
      persisting.tell(PersistAll(Vector("a")), probe.ref)
      SteppingInmemJournal.step(journal)
      probe.expectMsg(Persisted("a"))
      watch(persisting)
      system.stop(persisting)
      expectTerminated(persisting)

      val shortLivedRequester = TestProbe()
      journal.tell(ReplayMessagesWithBatching(actorInstanceId = 23), shortLivedRequester.ref)
      journal.tell(Identify("batching-request-registered"), testActor)
      expectMsg(ActorIdentity("batching-request-registered", Some(journal)))
      watch(shortLivedRequester.ref)
      system.stop(shortLivedRequester.ref)
      expectTerminated(shortLivedRequester.ref)
      journal.tell(Identify("batching-request-removed"), testActor)
      expectMsg(ActorIdentity("batching-request-removed", Some(journal)))

      val published = TestProbe()
      system.eventStream.subscribe(published.ref, classOf[ReplayMessages])
      journal.tell(
        ReplayMessages(1L, Long.MaxValue, Long.MaxValue, persistenceId, shortLivedRequester.ref),
        shortLivedRequester.ref)
      published.expectNoMessage(100.millis)
      SteppingInmemJournal.step(journal)
      SteppingInmemJournal.step(journal)
      published.expectMsgType[ReplayMessages].persistenceId shouldBe persistenceId
      system.eventStream.unsubscribe(published.ref)
    }

    "ignore a replay completion from an earlier journal actor incarnation" in {
      val control = RestartingReplayJournal.prepare()
      val journal = Persistence(system).journalFor(RestartingJournalId)
      Await.result(control.incarnations.head, 3.seconds)

      val firstRequester = TestProbe()
      journal.tell(ReplayMessagesWithBatching(actorInstanceId = 31), firstRequester.ref)
      journal.tell(
        ReplayMessages(1L, Long.MaxValue, Long.MaxValue, "old-journal-incarnation", firstRequester.ref),
        firstRequester.ref)
      Await.result(control.reads.head, 3.seconds)

      journal ! RestartingReplayJournal.Crash
      Await.result(control.incarnations(1), 3.seconds)

      val secondRequester = TestProbe()
      journal.tell(ReplayMessagesWithBatching(actorInstanceId = 32), secondRequester.ref)
      journal.tell(
        ReplayMessages(1L, Long.MaxValue, Long.MaxValue, "new-journal-incarnation", secondRequester.ref),
        secondRequester.ref)
      Await.result(control.reads(1), 3.seconds)

      RestartingReplayJournal.completeRead(read = 0, highestSequenceNr = 0L)
      secondRequester.expectNoMessage(100.millis)

      RestartingReplayJournal.completeRead(read = 1, highestSequenceNr = 0L)
      val response = secondRequester.expectMsgType[ReplayBatchResponse]
      response.actorInstanceId shouldBe 32
      response.response shouldBe RecoverySuccess(0L)
      secondRequester.lastSender shouldBe system.deadLetters
    }
  }
}
