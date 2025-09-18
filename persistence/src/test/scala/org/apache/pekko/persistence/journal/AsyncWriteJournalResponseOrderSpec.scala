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
        extraConfig = Some(
          s"""
          |pekko.persistence.journal.reverse-plugin {
          |  with-global-order {
          |    class = "${classOf[AsyncWriteJournalResponseOrderSpec.ReversePlugin].getName}"
          |    
          |    write-response-global-order = on
          |  }
          |  no-global-order {
          |    class = "${classOf[AsyncWriteJournalResponseOrderSpec.ReversePlugin].getName}"
          |    
          |    write-response-global-order = off
          |  }
          |}
          |""".stripMargin
        ))) with ImplicitSender {

  import AsyncWriteJournalResponseOrderSpec._

  "AsyncWriteJournal" must {
    "return write responses in request order if global response order is enabled" in {
      val pluginRef =
        extension.journalFor(journalPluginId = "pekko.persistence.journal.reverse-plugin.with-global-order")

      pluginRef ! mkWriteMessages(1)
      pluginRef ! mkWriteMessages(2)
      pluginRef ! mkWriteMessages(3)

      pluginRef ! CompleteWriteOps

      getMessageNumsFromResponses(receiveN(6)) shouldEqual Vector(1, 2, 3)
    }

    "return write responses in completion order if global response order is disabled" in {
      val pluginRef =
        extension.journalFor(journalPluginId = "pekko.persistence.journal.reverse-plugin.no-global-order")

      pluginRef ! mkWriteMessages(1)
      pluginRef ! mkWriteMessages(2)
      pluginRef ! mkWriteMessages(3)

      pluginRef ! CompleteWriteOps

      getMessageNumsFromResponses(receiveN(6)) shouldEqual Vector(3, 2, 1)
    }
  }

  private def mkWriteMessages(num: Int): JournalProtocol.WriteMessages = JournalProtocol.WriteMessages(
    messages = Vector(AtomicWrite(PersistentRepr(
      payload = num,
      sequenceNr = 0L,
      persistenceId = num.toString
    ))),
    persistentActor = self,
    actorInstanceId = 1
  )

  private def getMessageNumsFromResponses(responses: Seq[AnyRef]): Vector[Int] = responses.collect {
    case successResponse: JournalProtocol.WriteMessageSuccess =>
      successResponse.persistent.payload.asInstanceOf[Int]
  }.toVector
}

private object AsyncWriteJournalResponseOrderSpec {
  case object CompleteWriteOps

  /**
   * Accumulates asyncWriteMessages requests and completes them in reverse receive order on [[CompleteWriteOps]] command
   */
  class ReversePlugin extends AsyncWriteJournal {

    private implicit val ec: ExecutionContext = context.dispatcher

    private var pendingOps: Vector[Promise[Unit]] = Vector.empty

    override def receivePluginInternal: Receive = {
      case CompleteWriteOps =>
        pendingOps.reverse.foreach(_.success(()))
        pendingOps = Vector.empty
    }

    override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
      val responsePromise = Promise[Unit]()
      pendingOps = pendingOps :+ responsePromise
      responsePromise.future.map(_ => Vector.empty)
    }

    override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = ???

    override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
        recoveryCallback: PersistentRepr => Unit): Future[Unit] = ???

    override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = ???
  }
}
