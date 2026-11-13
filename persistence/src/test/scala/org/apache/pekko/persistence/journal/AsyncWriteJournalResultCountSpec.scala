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

package org.apache.pekko.persistence.journal

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try

import org.apache.pekko
import pekko.persistence.{ AtomicWrite, JournalProtocol, PersistenceSpec, PersistentEnvelope, PersistentRepr }
import pekko.testkit.ImplicitSender

class AsyncWriteJournalResultCountSpec
    extends PersistenceSpec(
      PersistenceSpec.config(
        plugin = "",
        test = classOf[AsyncWriteJournalResultCountSpec].getSimpleName,
        extraConfig = Some(
          s"""
          |${AsyncWriteJournalResultCountSpec.PluginId} {
          |  class = "${classOf[AsyncWriteJournalResultCountSpec.FirstWriteOnlyJournal].getName}"
          |  plugin-dispatcher = "pekko.actor.default-dispatcher"
          |}
          |""".stripMargin
        ))) with ImplicitSender {

  "AsyncWriteJournal" must {
    "report the number of AtomicWrites in the request when asyncWriteMessages returns too few results" in {
      val pluginRef = extension.journalFor(journalPluginId = AsyncWriteJournalResultCountSpec.PluginId)

      pluginRef ! JournalProtocol.WriteMessages(
        messages = Vector(atomicWrite(1L), atomicWrite(2L)),
        persistentActor = self,
        actorInstanceId = 1)

      val failed = expectMsgType[JournalProtocol.WriteMessagesFailed]
      failed.writeCount shouldEqual 2
      failed.cause shouldBe an[IllegalStateException]
      failed.cause.getMessage should include("Expected [2], but got [1]")
      expectMsgType[JournalProtocol.WriteMessageFailure]
      expectMsgType[JournalProtocol.WriteMessageFailure]
    }
  }

  private def atomicWrite(sequenceNr: Long): AtomicWrite =
    AtomicWrite(PersistentRepr(payload = "", sequenceNr = sequenceNr, persistenceId = "pid"))
}

object AsyncWriteJournalResultCountSpec {
  val PluginId: String = "pekko.persistence.journal.first-write-only-journal"

  /**
   * Prepares only the first AtomicWrite of a batch and returns one result for it, so the
   * number of results matches the prepared batch but not the AtomicWrites in the request.
   */
  final class FirstWriteOnlyJournal extends AsyncWriteJournal {

    override protected def preparePersistentBatch(rb: immutable.Seq[PersistentEnvelope]): immutable.Seq[AtomicWrite] =
      super.preparePersistentBatch(rb).take(1)

    override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] =
      Future.successful(messages.map(_ => AsyncWriteJournal.successUnit))

    override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = ???

    override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
        recoveryCallback: PersistentRepr => Unit): Future[Unit] = ???

    override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = ???
  }
}
