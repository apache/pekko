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

package org.apache.pekko.persistence.typed.internal

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.pattern.StatusReply
import pekko.persistence.AtomicWrite
import pekko.persistence.JournalProtocol

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

object EventWriterSpec {
  def config =
    ConfigFactory.parseString(s"""
      pekko.persistence.journal.inmem.delay-writes=10ms
    """).withFallback(ConfigFactory.load()).resolve()
}

class EventWriterSpec extends ScalaTestWithActorTestKit(EventWriterSpec.config) with AnyWordSpecLike with LogCapturing {

  private val settings = EventWriter.EventWriterSettings(10, 5.seconds)
  implicit val ec: ExecutionContext = testKit.system.executionContext

  "The event writer" should {

    "handle duplicates" in new TestSetup {
      sendWrite(1)
      journalAckWrite()
      clientExpectSuccess(1)

      // should also be ack:ed
      sendWrite(1)
      journalAckWrite()
      clientExpectSuccess(1)
    }

    "handle batched duplicates" in new TestSetup {
      // first write
      sendWrite(1)
      // first batch
      for (n <- 2L to 10L) {
        sendWrite(n)
      }
      // 0 will be written directly
      journalAckWrite() should ===(1)
      clientExpectSuccess(1)

      // completing 1 triggers write of batch with 0-9
      // second
      for (n <- 1L to 10L) {
        sendWrite(n)
      }
      // batch 0-9 in flight, writes in the meanwhile go in a new batch
      journalAckWrite() should ===(9)
      journalFailWrite("duplicate") should ===(10)
      journalHighestSeqNr(10L)

      clientExpectSuccess(19)
    }

    "handle batches with half duplicates" in new TestSetup {
      for (n <- 1L to 10L) {
        sendWrite(n)
      }
      journalAckWrite() should ===(1)
      journalAckWrite() should ===(9)
      clientExpectSuccess(10)

      for (n <- 5L until 15L) {
        sendWrite(n)
      }
      journalFailWrite("duplicate") should ===(1) // seq nr 5
      journalHighestSeqNr(10L)
      journalFailWrite("duplicate") should ===(9) // batch of 6-15
      journalHighestSeqNr(10L)
      journalAckWrite() should ===(4) // new write of 11-15 (non duplicates)

      // all writes succeeded
      clientExpectSuccess(10)
    }

    "pass real errors from journal back" in new TestSetup {
      sendWrite(1L)
      journalFailWrite("error error")
      // duplicate handling will ask for highest seq nr, can't know it is an actual error
      journalHighestSeqNr(0L)
      val response = clientProbe.receiveMessage()
      response.isError should ===(true)
      response.getError.getMessage should ===("Journal write failed")
    }

    "ignores old failures when replay triggered" in new TestSetup {
      sendWrite(1L) // triggers write

      sendWrite(1L) // goes into batch
      sendWrite(2L)
      journalAckWrite()

      val firstWrite = fakeJournal.expectMessageType[JournalProtocol.WriteMessages]
      val payloads = firstWrite.messages.head.asInstanceOf[AtomicWrite].payload
      // signal first failure, triggers HighestSeq
      firstWrite.persistentActor ! JournalProtocol.WriteMessageFailure(
        payloads.head,
        new RuntimeException("duplicate"),
        firstWrite.actorInstanceId)

      // replay response triggers partial rewrite, seq nr 2
      val replayRequest = fakeJournal.expectMessageType[JournalProtocol.ReplayMessages]
      replayRequest.persistentActor ! JournalProtocol.RecoverySuccess(1L)

      // but then original write failure arrives for seq nr 2 after sequence number lookup succeeded
      firstWrite.persistentActor ! JournalProtocol.WriteMessageFailure(
        payloads.tail.head,
        new RuntimeException("duplicate"),
        firstWrite.actorInstanceId)
      firstWrite.persistentActor ! JournalProtocol.WriteMessagesFailed

      // ack the partial retry
      journalAckWrite() should ===(1)

      clientExpectSuccess(3)

    }

    "handle writes to many pids" in {
      val writer = spawn(EventWriter("pekko.persistence.journal.inmem", settings))
      val probe = createTestProbe[StatusReply[EventWriter.WriteAck]]()
      (0 to 1000).map { pidN =>
        Future {
          for (n <- 0 until 20) {
            writer ! EventWriter.Write(s"pid$pidN", n.toLong, n.toString, None, Set.empty, probe.ref)
          }
        }
      }
      probe.receiveMessages(20 * 1000, 20.seconds)
    }
  }

  trait TestSetup {
    def pid1 = "pid1"
    val fakeJournal = createTestProbe[JournalProtocol.Message]()
    val writer = spawn(EventWriter(fakeJournal.ref, settings))
    val clientProbe = createTestProbe[StatusReply[EventWriter.WriteAck]]()
    def sendWrite(seqNr: Long, pid: String = pid1): Unit = {
      writer ! EventWriter.Write(pid, seqNr, seqNr.toString, None, Set.empty, clientProbe.ref)
    }
    def journalAckWrite(pid: String = pid1): Int = {
      val write = fakeJournal.expectMessageType[JournalProtocol.WriteMessages]
      write.messages should have size (1)
      val atomicWrite = write.messages.head.asInstanceOf[AtomicWrite]
      atomicWrite.payload.foreach { repr =>
        repr.persistenceId should ===(pid)
        write.persistentActor ! JournalProtocol.WriteMessageSuccess(repr, write.actorInstanceId)
      }
      write.persistentActor ! JournalProtocol.WriteMessagesSuccessful
      atomicWrite.payload.size
    }

    def journalFailWrite(reason: String, pid: String = pid1): Int = {
      val write = fakeJournal.expectMessageType[JournalProtocol.WriteMessages]
      write.messages should have size (1)
      val atomicWrite = write.messages.head.asInstanceOf[AtomicWrite]
      atomicWrite.payload.foreach { repr =>
        repr.persistenceId should ===(pid)
        write.persistentActor ! JournalProtocol.WriteMessageFailure(
          repr,
          new RuntimeException(reason),
          write.actorInstanceId)
      }
      write.persistentActor ! JournalProtocol.WriteMessagesFailed
      atomicWrite.payload.size
    }

    def journalHighestSeqNr(highestSeqNr: Long): Unit = {
      val replay = fakeJournal.expectMessageType[JournalProtocol.ReplayMessages]
      replay.persistentActor ! JournalProtocol.RecoverySuccess(highestSeqNr)
    }

    def clientExpectSuccess(n: Int) = {
      clientProbe.receiveMessages(n).foreach { reply =>
        reply.isSuccess should be(true)
      }
    }
  }

}
