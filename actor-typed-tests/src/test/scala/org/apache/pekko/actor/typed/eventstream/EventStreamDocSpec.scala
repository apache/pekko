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

package org.apache.pekko.actor.typed.eventstream

import org.apache.pekko.actor.{ AllDeadLetters, DeadLetter, Dropped, SuppressedDeadLetter, UnhandledMessage }
import org.apache.pekko.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.wordspec.AnyWordSpecLike

object EventStreamDocSpec {

  // #listen-to-dead-letters
  import org.apache.pekko.actor.typed.Behavior
  import org.apache.pekko.actor.typed.eventstream.EventStream.Subscribe
  import org.apache.pekko.actor.typed.scaladsl.Behaviors

  object DeadLetterListener {
    sealed trait Command
    final case class DeadLetterWrapper(deadLetter: DeadLetter) extends Command

    def apply(): Behavior[Command] =
      Behaviors.setup[Command] {
        context =>
          val adapter = context.messageAdapter[DeadLetter](DeadLetterWrapper.apply)
          context.system.eventStream ! Subscribe(adapter)

          Behaviors.receiveMessage {
            case DeadLetterWrapper(DeadLetter(message, sender, recipient)) =>
              context.log.info("Dead letter received from sender ({}) to recipient ({}) with message: {}",
                sender.path.name, recipient.path.name, message.toString)
              Behaviors.same
          }
      }
  }
  // #listen-to-dead-letters

  // #listen-to-super-class
  object AllDeadLetterListener {
    sealed trait Command
    final case class AllDeadLettersWrapper(allDeadLetters: AllDeadLetters) extends Command

    def apply(): Behavior[Command] =
      Behaviors.setup[Command] {
        context =>
          val adapter = context.messageAdapter[AllDeadLetters](AllDeadLettersWrapper.apply)
          context.system.eventStream ! Subscribe(adapter)

          Behaviors.receiveMessage {
            case AllDeadLettersWrapper(allDeadLetters) =>
              allDeadLetters match {
                case DeadLetter(message, sender, recipient) =>
                  context.log.info("DeadLetter received from sender ({}) to recipient ({}) with message: {}",
                    sender.path.name, recipient.path.name, message.toString)

                case Dropped(message, reason, sender, recipient) =>
                  context.log.info("Dropped: sender ({}) to recipient ({}) with message: {}, reason: {}",
                    sender.path.name, recipient.path.name, message.toString, reason)

                case SuppressedDeadLetter(message, sender, recipient) =>
                  // use trace otherwise logs will be flooded
                  context.log.trace("SuppressedDeadLetter received from sender ({}) to recipient ({}) with message: {}",
                    sender.path.name, recipient.path.name, message)

                case UnhandledMessage(message, sender, recipient) =>
                  context.log.info("UnhandledMessage received from sender ({}) to recipient ({}) with message: {}",
                    sender.path.name, recipient.path.name, message.toString)
              }
              Behaviors.same
          }
      }
  }
  // #listen-to-super-class
}

class EventStreamDocSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {
  import EventStreamDocSpec._

  "listen to dead letters" in {
    // #listen-to-dead-letters

    ActorSystem(Behaviors.setup[Void] { context =>
        context.spawn(DeadLetterListener(), "DeadLetterListener")
        Behaviors.empty
      }, "DeadLetterListenerSystem")
    // #listen-to-dead-letters
  }

  "listen to all dead letters" in {
    // #listen-to-super-class

    ActorSystem(Behaviors.setup[Void] { context =>
        context.spawn(AllDeadLetterListener(), "AllDeadLetterListener")
        Behaviors.empty
      }, "AllDeadLetterListenerSystem")
    // #listen-to-super-class
  }
}
