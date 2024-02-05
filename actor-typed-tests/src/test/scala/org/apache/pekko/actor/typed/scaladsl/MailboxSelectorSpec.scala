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

package org.apache.pekko.actor.typed.scaladsl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.actor.ActorCell
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.DispatcherSelector
import pekko.actor.typed.MailboxSelector
import pekko.actor.typed.Props
import pekko.actor.typed.internal.adapter.ActorContextAdapter
import pekko.actor.typed.scaladsl.AskPattern._
import pekko.dispatch.BoundedMessageQueueSemantics
import pekko.dispatch.BoundedNodeMessageQueue
import pekko.dispatch.Dispatchers
import pekko.dispatch.MessageQueue
import pekko.dispatch.NodeMessageQueue
import org.scalatest.wordspec.AnyWordSpecLike

object MailboxSelectorSpec {
  val config = ConfigFactory.parseString(
    """
      specific-mailbox {
        mailbox-type = "org.apache.pekko.dispatch.NonBlockingBoundedMailbox"
        mailbox-capacity = 4
      }
    """)

  object PingPong {
    case class Ping(replyTo: ActorRef[Pong])

    case class Pong(threadName: String)

    def apply(): Behavior[Ping] =
      Behaviors.receiveMessage[Ping] { message =>
        message.replyTo ! Pong(Thread.currentThread().getName)
        Behaviors.same
      }

  }

}

class MailboxSelectorSpec(config: Config)
    extends ScalaTestWithActorTestKit(config)
    with AnyWordSpecLike
    with LogCapturing {

  def this() = this(MailboxSelectorSpec.config)

  sealed trait Command
  case class WhatsYourMailbox(replyTo: ActorRef[MessageQueue]) extends Command
  case class WhatsYourDispatcher(replyTo: ActorRef[String]) extends Command

  private def extract[R](context: ActorContext[_], f: ActorCell => R): R = {
    context match {
      case adapter: ActorContextAdapter[_] =>
        adapter.classicActorContext match {
          case cell: ActorCell => f(cell)
          case unexpected      => throw new RuntimeException(s"Unexpected: $unexpected")
        }
      case unexpected => throw new RuntimeException(s"Unexpected: $unexpected")
    }
  }

  private def behavior: Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage[Command] {
        case WhatsYourMailbox(replyTo) =>
          replyTo ! extract(context, cell => cell.mailbox.messageQueue)
          Behaviors.same
        case WhatsYourDispatcher(replyTo) =>
          replyTo ! extract(context, cell => cell.dispatcher.id)
          Behaviors.same
      }
    }

  "MailboxSelectorSpec" must {

    "default is unbounded" in {
      val actor = spawn(behavior)
      val mailbox = actor.ask(WhatsYourMailbox(_)).futureValue
      mailbox shouldBe a[NodeMessageQueue]
    }

    "select an specific mailbox from MailboxSelector " in {
      val actor = spawn(behavior, MailboxSelector.fromConfig("specific-mailbox"))
      val mailbox = actor.ask(WhatsYourMailbox(_)).futureValue
      mailbox shouldBe a[BoundedMessageQueueSemantics]
      mailbox.asInstanceOf[BoundedNodeMessageQueue].capacity should ===(4)
    }

    "select an specific mailbox from empty Props " in {
      val actor = spawn(behavior, Props.empty.withMailboxFromConfig("specific-mailbox"))
      val mailbox = actor.ask(WhatsYourMailbox(_)).futureValue
      mailbox shouldBe a[BoundedMessageQueueSemantics]
      mailbox.asInstanceOf[BoundedNodeMessageQueue].capacity should ===(4)
    }

    "select an specific mailbox from DispatcherSelector " in {
      val actor = spawn(behavior, DispatcherSelector.blocking().withMailboxFromConfig("specific-mailbox"))
      val mailbox = actor.ask(WhatsYourMailbox(_)).futureValue
      mailbox shouldBe a[BoundedMessageQueueSemantics]
      mailbox.asInstanceOf[BoundedNodeMessageQueue].capacity should ===(4)
      val dispatcher = actor.ask(WhatsYourDispatcher(_)).futureValue
      dispatcher shouldBe Dispatchers.DefaultBlockingDispatcherId
    }

  }

}
