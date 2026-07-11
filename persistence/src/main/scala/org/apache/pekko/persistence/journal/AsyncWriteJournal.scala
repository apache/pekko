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

package org.apache.pekko.persistence.journal

import java.util.concurrent.atomic.AtomicLong

import scala.collection.{ immutable, mutable }
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor._
import pekko.annotation.InternalApi
import pekko.pattern.CircuitBreaker
import pekko.pattern.pipe
import pekko.persistence._
import pekko.util.Helpers.toRootLowerCase

/**
 * Abstract journal, optimized for asynchronous, non-blocking writes.
 */
trait AsyncWriteJournal extends Actor with WriteJournalBase with AsyncRecovery {
  import AsyncWriteJournal._
  import JournalProtocol._

  private val extension = Persistence(context.system)
  private val publish = extension.settings.internal.publishPluginCommands
  private val config = extension.configFor(self)

  private val breaker = {
    val maxFailures = config.getInt("circuit-breaker.max-failures")
    val callTimeout = config.getDuration("circuit-breaker.call-timeout", MILLISECONDS).millis
    val resetTimeout = config.getDuration("circuit-breaker.reset-timeout", MILLISECONDS).millis
    CircuitBreaker(context.system.scheduler, maxFailures, callTimeout, resetTimeout)
  }

  private val replayFilterMode: ReplayFilter.Mode =
    toRootLowerCase(config.getString("replay-filter.mode")) match {
      case "off"                   => ReplayFilter.Disabled
      case "repair-by-discard-old" => ReplayFilter.RepairByDiscardOld
      case "fail"                  => ReplayFilter.Fail
      case "warn"                  => ReplayFilter.Warn
      case other                   =>
        throw new IllegalArgumentException(
          s"invalid replay-filter.mode [$other], supported values [off, repair-by-discard-old, fail, warn]")
    }
  private def isReplayFilterEnabled: Boolean = replayFilterMode != ReplayFilter.Disabled
  private val replayFilterWindowSize: Int = config.getInt("replay-filter.window-size")
  private val replayFilterMaxOldWriters: Int = config.getInt("replay-filter.max-old-writers")

  private val resequencer = context.actorOf(Props[Resequencer]())
  private var resequencerCounter = 1L

  final def receive = receiveWriteJournal.orElse[Any, Unit](receivePluginInternal)

  final val receiveWriteJournal: Actor.Receive = {
    // cannot be a val in the trait due to binary compatibility
    val replayDebugEnabled: Boolean = config.getBoolean("replay-filter.debug")
    val enableGlobalWriteResponseOrder: Boolean = config.getBoolean("write-response-global-order")
    val replayBatchSize: Long = config.getLong("replay-batch-size")
    require(replayBatchSize > 0L, s"replay-batch-size must be greater than 0, but was [$replayBatchSize]")

    val eventStream = context.system.eventStream // used from Future callbacks
    implicit val ec: ExecutionContext = context.dispatcher

    val batchingReplayRequesters = mutable.Map.empty[ActorRef, Int]
    val replaySessions = mutable.Map.empty[Long, ReplaySession]

    def replayTarget(persistentActor: ActorRef): ActorRef =
      if (isReplayFilterEnabled)
        context.actorOf(
          ReplayFilter.props(
            persistentActor,
            replayFilterMode,
            replayFilterWindowSize,
            replayFilterMaxOldWriters,
            replayDebugEnabled))
      else persistentActor

    def batchedReplayTarget(persistentActor: ActorRef, actorInstanceId: Int): (ActorRef, ActorRef) = {
      val relay = context.actorOf(replayForwarderProps(persistentActor, actorInstanceId))
      val replyTo =
        if (isReplayFilterEnabled)
          context.actorOf(
            ReplayFilter.props(
              relay,
              replayFilterMode,
              replayFilterWindowSize,
              replayFilterMaxOldWriters,
              replayDebugEnabled))
        else relay
      (replyTo, relay)
    }

    def hasReplaySession(persistentActor: ActorRef): Boolean =
      replaySessions.valuesIterator.exists(_.request.persistentActor == persistentActor)

    def unwatchIfUnused(persistentActor: ActorRef): Unit =
      if (!batchingReplayRequesters.contains(persistentActor) && !hasReplaySession(persistentActor))
        context.unwatch(persistentActor)

    def removeReplaySession(replayId: Long): Option[ReplaySession] =
      replaySessions.remove(replayId).map { session =>
        val persistentActor = session.request.persistentActor
        unwatchIfUnused(persistentActor)
        session
      }

    def publishReplayRequest(session: ReplaySession): Unit =
      if (publish) eventStream.publish(session.request)

    def finishReplay(replayId: Long, response: JournalProtocol.Response): Unit =
      removeReplaySession(replayId).foreach { session =>
        session.replyTo.tell(response, self)
        publishReplayRequest(session)
      }

    def cancelReplay(replayId: Long): Unit =
      removeReplaySession(replayId).foreach { session =>
        if (session.replyTo != session.relay)
          context.stop(session.replyTo)
        context.stop(session.relay)
        publishReplayRequest(session)
      }

    def cancelReplaysFor(persistentActor: ActorRef): Unit =
      replaySessions.valuesIterator
        .filter(_.request.persistentActor == persistentActor)
        .map(_.replayId)
        .toVector
        .foreach(cancelReplay)

    def batchUpperBound(fromSequenceNr: Long, toSequenceNr: Long): Long = {
      val increment = replayBatchSize - 1L
      val upperBound =
        if (fromSequenceNr > Long.MaxValue - increment) Long.MaxValue
        else fromSequenceNr + increment
      math.min(toSequenceNr, upperBound)
    }

    def startReplayBatch(session: ReplaySession): Unit = {
      val batchToSequenceNr = batchUpperBound(session.nextSequenceNr, session.toSequenceNr)
      val batchMax = math.min(replayBatchSize, session.remaining)
      val replayed = new AtomicLong
      val replayResult =
        try
          asyncReplayMessages(
            session.request.persistenceId,
            session.nextSequenceNr,
            batchToSequenceNr,
            batchMax) { p =>
            replayed.incrementAndGet()
            if (!p.deleted) // old records from Akka 2.3 may still have the deleted flag
              adaptFromJournal(p).foreach { adaptedPersistentRepr =>
                session.replyTo.tell(ReplayedMessage(adaptedPersistentRepr), self)
              }
          }
        catch { case NonFatal(e) => Future.failed(e) }

      replayResult
        .map(_ => ReplayBatchCompleted(session.replayId, batchToSequenceNr, replayed.get()))
        .recover { case e => ReplayFailed(session.replayId, e) }
        .pipeTo(self)
    }

    def startBatchedReplay(request: ReplayMessages, actorInstanceId: Int): Unit = {
      val replayId = nextReplayId()
      val (replyTo, relay) = batchedReplayTarget(request.persistentActor, actorInstanceId)
      val session = ReplaySession(
        replayId,
        request,
        replyTo,
        relay,
        highestSequenceNr = 0L,
        toSequenceNr = 0L,
        nextSequenceNr = request.fromSequenceNr,
        remaining = request.max,
        awaitingAck = false)
      replaySessions.put(replayId, session)
      context.watch(request.persistentActor)

      val readHighestSequenceNrFrom = math.max(0L, request.fromSequenceNr - 1L)
      val readHighestResult =
        try breaker.withCircuitBreaker(asyncReadHighestSequenceNr(request.persistenceId, readHighestSequenceNrFrom))
        catch { case NonFatal(e) => Future.failed(e) }
      readHighestResult
        .map(ReplayHighestSequenceNr(replayId, _))
        .recover { case e => ReplayFailed(replayId, e) }
        .pipeTo(self)
    }

    // should be a private method in the trait, but it needs the enableGlobalWriteResponseOrder field which can't be
    // moved to the trait level because adding any fields there breaks bincompat
    @InternalApi
    def sendWriteResponse(msg: Any, snr: Long, target: ActorRef, sender: ActorRef): Unit = {
      if (enableGlobalWriteResponseOrder) {
        resequencer ! Desequenced(msg, snr, target, sender)
      } else {
        target.tell(msg, sender)
      }
    }

    {
      case WriteMessages(messages, persistentActor, actorInstanceId) =>
        val cctr = resequencerCounter
        resequencerCounter += messages.foldLeft(1)((acc, m) => acc + m.size)

        val atomicWriteCount = messages.count(_.isInstanceOf[AtomicWrite])
        val prepared = Try(preparePersistentBatch(messages))
        val writeResult = (prepared match {
          case Success(prep) if prep.isEmpty =>
            // prep is empty when all messages are instances of NonPersistentRepr (used for defer) in that case,
            // we continue right away without calling the journal plugin (most plugins fail calling head on empty Seq).
            // Ordering of the replies is handled by Resequencer
            Future.successful(Nil)
          case Success(prep) =>
            // try in case the asyncWriteMessages throws
            try breaker.withCircuitBreaker(asyncWriteMessages(prep))
            catch { case NonFatal(e) => Future.failed(e) }
          case f @ Failure(_) =>
            // exception from preparePersistentBatch => rejected
            Future.successful(messages.collect { case _: AtomicWrite => f })
        }).map { results =>
          if (results.nonEmpty && results.size != atomicWriteCount)
            throw new IllegalStateException(
              "asyncWriteMessages returned invalid number of results. " +
              s"Expected [${prepared.get.size}], but got [${results.size}]")
          results
        }

        writeResult.onComplete {
          case Success(results) =>
            sendWriteResponse(WriteMessagesSuccessful, cctr, persistentActor, self)

            val resultsIter =
              if (results.isEmpty) Iterator.fill(atomicWriteCount)(AsyncWriteJournal.successUnit)
              else results.iterator
            var n = cctr + 1
            messages.foreach {
              case a: AtomicWrite =>
                resultsIter.next() match {
                  case Success(_) =>
                    a.payload.foreach { p =>
                      sendWriteResponse(WriteMessageSuccess(p, actorInstanceId), n, persistentActor, p.sender)
                      n += 1
                    }
                  case Failure(e) =>
                    a.payload.foreach { p =>
                      sendWriteResponse(
                        WriteMessageRejected(p, e, actorInstanceId),
                        n,
                        persistentActor,
                        p.sender)
                      n += 1
                    }
                }

              case r: NonPersistentRepr =>
                sendWriteResponse(LoopMessageSuccess(r.payload, actorInstanceId), n, persistentActor, r.sender)
                n += 1
            }

          case Failure(e) =>
            sendWriteResponse(WriteMessagesFailed(e, atomicWriteCount), cctr, persistentActor, self)
            var n = cctr + 1
            messages.foreach {
              case a: AtomicWrite =>
                a.payload.foreach { p =>
                  sendWriteResponse(WriteMessageFailure(p, e, actorInstanceId), n, persistentActor, p.sender)
                  n += 1
                }
              case r: NonPersistentRepr =>
                sendWriteResponse(LoopMessageSuccess(r.payload, actorInstanceId), n, persistentActor, r.sender)
                n += 1
            }
        }

      case ReplayMessagesWithBatching(actorInstanceId) =>
        val requester = sender()
        cancelReplaysFor(requester)
        batchingReplayRequesters.put(requester, actorInstanceId)
        context.watch(requester)

      case ReplayMessagesCancel =>
        val requester = sender()
        batchingReplayRequesters.remove(requester)
        cancelReplaysFor(requester)
        unwatchIfUnused(requester)

      case r @ ReplayMessages(fromSequenceNr, toSequenceNr, max, persistenceId, persistentActor) =>
        val requester = sender()
        if (requester == persistentActor)
          cancelReplaysFor(persistentActor)
        val requestedBatching = batchingReplayRequesters.remove(requester)
        if (requestedBatching.isDefined && requester == persistentActor) {
          startBatchedReplay(r, requestedBatching.get)
        } else {
          if (requestedBatching.isDefined)
            unwatchIfUnused(requester)
          val replyTo = replayTarget(persistentActor)

          val readHighestSequenceNrFrom = math.max(0L, fromSequenceNr - 1)
          /*
           * The API docs for the [[AsyncRecovery]] say not to rely on asyncReadHighestSequenceNr
           * being called before a call to asyncReplayMessages even tho it currently always is. The Cassandra
           * plugin does rely on this so if you change this change the Cassandra plugin.
           */
          breaker
            .withCircuitBreaker(asyncReadHighestSequenceNr(persistenceId, readHighestSequenceNrFrom))
            .flatMap { highSeqNr =>
              val toSeqNr = math.min(toSequenceNr, highSeqNr)
              if (toSeqNr <= 0L || fromSequenceNr > toSeqNr)
                Future.successful(highSeqNr)
              else {
                // Send replayed messages and replay result to persistentActor directly. No need
                // to resequence replayed messages relative to written and looped messages.
                // not possible to use circuit breaker here
                asyncReplayMessages(persistenceId, fromSequenceNr, toSeqNr, max) { p =>
                  if (!p.deleted) // old records from Akka 2.3 may still have the deleted flag
                    adaptFromJournal(p).foreach { adaptedPersistentRepr =>
                      replyTo.tell(ReplayedMessage(adaptedPersistentRepr), Actor.noSender)
                    }
                }.map(_ => highSeqNr)
              }
            }
            .map { highSeqNr =>
              RecoverySuccess(highSeqNr)
            }
            .recover {
              case e => ReplayMessagesFailure(e)
            }
            .pipeTo(replyTo)
            .foreach { _ =>
              if (publish) eventStream.publish(r)
            }
        }

      case ReplayHighestSequenceNr(replayId, highSeqNr) =>
        replaySessions.get(replayId).foreach { session =>
          val toSeqNr = math.min(session.request.toSequenceNr, highSeqNr)
          val initialized = session.copy(highestSequenceNr = highSeqNr, toSequenceNr = toSeqNr)
          replaySessions.update(replayId, initialized)
          if (toSeqNr <= 0L || session.nextSequenceNr > toSeqNr || session.remaining <= 0L)
            finishReplay(replayId, RecoverySuccess(highSeqNr))
          else
            startReplayBatch(initialized)
        }

      case ReplayBatchCompleted(replayId, batchToSequenceNr, replayed) =>
        replaySessions.get(replayId).foreach { session =>
          val remaining = if (replayed >= session.remaining) 0L else session.remaining - replayed
          if (remaining == 0L || batchToSequenceNr >= session.toSequenceNr) {
            finishReplay(replayId, RecoverySuccess(session.highestSequenceNr))
          } else {
            val waiting = session.copy(
              nextSequenceNr = batchToSequenceNr + 1L,
              remaining = remaining,
              awaitingAck = true)
            replaySessions.update(replayId, waiting)
            session.replyTo.tell(ReplayBatchReady(replayId), self)
          }
        }

      case ReplayBatchAck(replayId) =>
        replaySessions.get(replayId) match {
          case Some(session) if session.awaitingAck && sender() == session.request.persistentActor =>
            val replaying = session.copy(awaitingAck = false)
            replaySessions.update(replayId, replaying)
            startReplayBatch(replaying)
          case _ => // stale or invalid acknowledgement
        }

      case ReplayBatchCancel(replayId) =>
        replaySessions.get(replayId) match {
          case Some(session) if sender() == session.replyTo => cancelReplay(replayId)
          case _                                            => // stale or invalid cancellation
        }

      case ReplayFailed(replayId, cause) =>
        finishReplay(replayId, ReplayMessagesFailure(cause))

      case Terminated(persistentActor) =>
        batchingReplayRequesters.remove(persistentActor)
        cancelReplaysFor(persistentActor)

      case d @ DeleteMessagesTo(persistenceId, toSequenceNr, persistentActor) =>
        breaker
          .withCircuitBreaker(asyncDeleteMessagesTo(persistenceId, toSequenceNr))
          .map { _ =>
            DeleteMessagesSuccess(toSequenceNr)
          }
          .recover {
            case e => DeleteMessagesFailure(e, toSequenceNr)
          }
          .pipeTo(persistentActor)
          .onComplete { _ =>
            if (publish) eventStream.publish(d)
          }
    }
  }

  // #journal-plugin-api
  /**
   * Plugin API: asynchronously writes a batch (`Seq`) of persistent messages to the
   * journal.
   *
   * The batch is only for performance reasons, i.e. all messages don't have to be written
   * atomically. Higher throughput can typically be achieved by using batch inserts of many
   * records compared to inserting records one-by-one, but this aspect depends on the
   * underlying data store and a journal implementation can implement it as efficient as
   * possible. Journals should aim to persist events in-order for a given `persistenceId`
   * as otherwise in case of a failure, the persistent state may be end up being inconsistent.
   *
   * Each `AtomicWrite` message contains the single `PersistentRepr` that corresponds to
   * the event that was passed to the `persist` method of the `PersistentActor`, or it
   * contains several `PersistentRepr` that corresponds to the events that were passed
   * to the `persistAll` method of the `PersistentActor`. All `PersistentRepr` of the
   * `AtomicWrite` must be written to the data store atomically, i.e. all or none must
   * be stored. If the journal (data store) cannot support atomic writes of multiple
   * events it should reject such writes with a `Try` `Failure` with an
   * `UnsupportedOperationException` describing the issue. This limitation should
   * also be documented by the journal plugin.
   *
   * If there are failures when storing any of the messages in the batch the returned
   * `Future` must be completed with failure. The `Future` must only be completed with
   * success when all messages in the batch have been confirmed to be stored successfully,
   * i.e. they will be readable, and visible, in a subsequent replay. If there is
   * uncertainty about if the messages were stored or not the `Future` must be completed
   * with failure.
   *
   * Data store connection problems must be signaled by completing the `Future` with
   * failure.
   *
   * The journal can also signal that it rejects individual messages (`AtomicWrite`) by
   * the returned `immutable.Seq[Try[Unit]]`. It is possible but not mandatory to reduce
   * number of allocations by returning `Future.successful(Nil)` for the happy path,
   * i.e. when no messages are rejected. Otherwise the returned `Seq` must have as many elements
   * as the input `messages` `Seq`. Each `Try` element signals if the corresponding
   * `AtomicWrite` is rejected or not, with an exception describing the problem. Rejecting
   * a message means it was not stored, i.e. it must not be included in a later replay.
   * Rejecting a message is typically done before attempting to store it, e.g. because of
   * serialization error.
   *
   * Data store connection problems must not be signaled as rejections.
   *
   * It is possible but not mandatory to reduce number of allocations by returning
   * `Future.successful(Nil)` for the happy path, i.e. when no messages are rejected.
   *
   * Calls to this method are serialized by the enclosing journal actor. If you spawn
   * work in asynchronous tasks it is alright that they complete the futures in any order,
   * but the actual writes for a specific persistenceId should be serialized to avoid
   * issues such as events of a later write are visible to consumers (query side, or replay)
   * before the events of an earlier write are visible.
   * A PersistentActor will not send a new WriteMessages request before the previous one
   * has been completed.
   *
   * Please note that the `sender` field of the contained PersistentRepr objects has been
   * nulled out (i.e. set to `ActorRef.noSender`) in order to not use space in the journal
   * for a sender reference that will likely be obsolete during replay.
   *
   * Please also note that requests for the highest sequence number may be made concurrently
   * to this call executing for the same `persistenceId`, in particular it is possible that
   * a restarting actor tries to recover before its outstanding writes have completed. In
   * the latter case it is highly desirable to defer reading the highest sequence number
   * until all outstanding writes have completed, otherwise the PersistentActor may reuse
   * sequence numbers.
   *
   * This call is protected with a circuit-breaker.
   */
  def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]]

  /**
   * Plugin API: asynchronously deletes all persistent messages up to `toSequenceNr`
   * (inclusive).
   *
   * This call is protected with a circuit-breaker.
   * Message deletion doesn't affect the highest sequence number of messages,
   * journal must maintain the highest sequence number and never decrease it.
   */
  def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit]

  /**
   * Plugin API
   *
   * Allows plugin implementers to use `f pipeTo self` and
   * handle additional messages for implementing advanced features
   */
  def receivePluginInternal: Actor.Receive = Actor.emptyBehavior
  // #journal-plugin-api

}

/**
 * INTERNAL API.
 */
private[persistence] object AsyncWriteJournal {
  import JournalProtocol._

  val successUnit: Success[Unit] = Success(())

  private val replayIdCounter = new AtomicLong

  private def nextReplayId(): Long = replayIdCounter.incrementAndGet()

  private final case class ReplaySession(
      replayId: Long,
      request: ReplayMessages,
      replyTo: ActorRef,
      relay: ActorRef,
      highestSequenceNr: Long,
      toSequenceNr: Long,
      nextSequenceNr: Long,
      remaining: Long,
      awaitingAck: Boolean)

  private final case class ReplayHighestSequenceNr(replayId: Long, highestSequenceNr: Long)
      extends NoSerializationVerificationNeeded
  private final case class ReplayBatchCompleted(replayId: Long, toSequenceNr: Long, replayed: Long)
      extends NoSerializationVerificationNeeded
  private final case class ReplayFailed(replayId: Long, cause: Throwable) extends NoSerializationVerificationNeeded

  private def replayForwarderProps(persistentActor: ActorRef, actorInstanceId: Int): Props =
    Props(new ReplayForwarder(persistentActor, actorInstanceId))

  private final class ReplayForwarder(persistentActor: ActorRef, actorInstanceId: Int) extends Actor {
    def receive = {
      case response: JournalProtocol.Response =>
        persistentActor.tell(ReplayBatchResponse(actorInstanceId, response), Actor.noSender)
        response match {
          case _: RecoverySuccess | _: ReplayMessagesFailure => context.stop(self)
          case _                                             =>
        }
    }
  }

  final case class Desequenced(msg: Any, snr: Long, target: ActorRef, sender: ActorRef)
      extends NoSerializationVerificationNeeded

  class Resequencer extends Actor {
    import scala.collection.mutable.Map

    private val delayed = Map.empty[Long, Desequenced]
    private var delivered = 0L

    def receive = {
      case d: Desequenced => resequence(d)
    }

    @scala.annotation.tailrec
    private def resequence(d: Desequenced): Unit = {
      if (d.snr == delivered + 1) {
        delivered = d.snr
        d.target.tell(d.msg, d.sender)
      } else {
        delayed += (d.snr -> d)
      }
      val ro = delayed.remove(delivered + 1)
      if (ro.isDefined) resequence(ro.get)
    }
  }
}
