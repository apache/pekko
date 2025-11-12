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

import org.apache.pekko.persistence.journal.AsyncWriteJournalResponseOrderSpec._

import scala.collection.{ immutable, mutable }
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try
import org.apache.pekko.persistence.{ AtomicWrite, JournalProtocol, PersistenceSpec, PersistentRepr }
import org.apache.pekko.testkit.ImplicitSender

/**
 * Verifies write response ordering logic for [[AsyncWriteJournal]].
 *
 * Checkout write-response-global-order config option for more information.
 */
class AsyncWriteJournalResponseOrderSpec
    extends PersistenceSpec(
      PersistenceSpec.config(
        plugin = "", // we will provide explicit plugin IDs later
        test = classOf[AsyncWriteJournalResponseOrderSpec].getSimpleName,
        // using the default system dispatcher to make sure write-response-global-order works with it
        // see: https://github.com/apache/pekko/pull/2434
        extraConfig = Some(
          s"""
          |${ControlledWriteCompletionPlugin.BaseId} {
          |  with-global-order {
          |    class = "${classOf[ControlledWriteCompletionPlugin].getName}"
          |    plugin-dispatcher = "pekko.actor.default-dispatcher"
          |    write-response-global-order = on
          |  }
          |  no-global-order {
          |    class = "${classOf[ControlledWriteCompletionPlugin].getName}"
          |    plugin-dispatcher = "pekko.actor.default-dispatcher"
          |    write-response-global-order = off
          |  }
          |}
          |""".stripMargin
        ))) with ImplicitSender {

  "AsyncWriteJournal" must {
    "return write responses in request order if global response order is enabled" in {
      val pluginRef =
        extension.journalFor(journalPluginId = s"${ControlledWriteCompletionPlugin.BaseId}.with-global-order")

      // request writes for persistence Ids 1..9
      1.to(9).foreach { persistenceId =>
        pluginRef ! mkWriteMessages(persistenceId = persistenceId)
      }
      // complete writes for all but the first persistence ID in reverse order
      pluginRef ! CompleteWriteOps(persistenceIdsInOrder = 2.to(9).toVector.reverse)
      // AsyncWriteJournal should hold the responses yet to preserve the response order
      expectNoMessage()
      // complete write for the first persistence ID
      pluginRef ! CompleteWriteOps(persistenceIdsInOrder = Vector(1))
      // now we should receive all responses in the order in which they have been requested
      getPersistenceIdsFromResponses(receiveN(18)) shouldEqual 1.to(9).toVector
    }

    "return write responses as soon as the operation is complete if global response order is disabled" in {
      val pluginRef =
        extension.journalFor(journalPluginId = s"${ControlledWriteCompletionPlugin.BaseId}.no-global-order")

      // request writes for persistence Ids 1..9
      1.to(9).foreach { persistenceId =>
        pluginRef ! mkWriteMessages(persistenceId = persistenceId)
      }
      // complete writes for all but the first persistence ID in reverse order
      pluginRef ! CompleteWriteOps(persistenceIdsInOrder = 2.to(9).toVector.reverse)
      // AsyncWriteJournal sends out write responses for 2..9 right away without waiting
      getPersistenceIdsFromResponses(receiveN(16)).toSet shouldEqual 2.to(9).toSet
      // complete write for the first persistence ID
      pluginRef ! CompleteWriteOps(persistenceIdsInOrder = Vector(1))
      // and now we finally receive the response for persistence ID 1
      getPersistenceIdsFromResponses(receiveN(2)) shouldEqual Vector(1)
    }
  }

  private def mkWriteMessages(persistenceId: Int): JournalProtocol.WriteMessages = JournalProtocol.WriteMessages(
    messages = Vector(AtomicWrite(PersistentRepr(
      payload = "",
      sequenceNr = 0L,
      persistenceId = persistenceId.toString
    ))),
    persistentActor = self,
    actorInstanceId = 1
  )

  private def getPersistenceIdsFromResponses(responses: Seq[AnyRef]): Vector[Int] = responses.collect {
    case successResponse: JournalProtocol.WriteMessageSuccess =>
      successResponse.persistent.persistenceId.toInt
  }.toVector
}

private object AsyncWriteJournalResponseOrderSpec {
  final case class CompleteWriteOps(persistenceIdsInOrder: Vector[Int])

  /**
   * Accumulates asyncWriteMessages requests (one for each persistence ID is expected)
   * and completes them in requested order on [[CompleteWriteOps]] command.
   */
  final class ControlledWriteCompletionPlugin extends AsyncWriteJournal {

    private implicit val ec: ExecutionContext = context.dispatcher

    private val pendingOps: mutable.HashMap[Int, Promise[Unit]] = mutable.HashMap.empty

    override def receivePluginInternal: Receive = {
      case cmd: CompleteWriteOps =>
        cmd.persistenceIdsInOrder.foreach { persistenceId =>
          pendingOps(persistenceId).success(())
          pendingOps -= persistenceId
        }
    }

    override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
      val responsePromise = Promise[Unit]()
      pendingOps.put(messages.head.persistenceId.toInt, responsePromise)
      responsePromise.future.map(_ => Vector.empty)
    }

    override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = ???

    override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
        recoveryCallback: PersistentRepr => Unit): Future[Unit] = ???

    override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = ???
  }

  object ControlledWriteCompletionPlugin {
    val BaseId: String = "pekko.persistence.journal.controlled-write-completion-plugin"
  }
}
