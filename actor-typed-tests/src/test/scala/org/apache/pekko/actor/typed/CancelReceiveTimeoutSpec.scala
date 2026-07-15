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

package org.apache.pekko.actor.typed

import scala.concurrent.duration._
import scala.reflect.ClassTag

import org.apache.pekko

import org.scalatest.wordspec.AnyWordSpecLike

import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter._

class CancelReceiveTimeoutSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  sealed trait Command
  case object SetAndCancelTimeout extends Command
  case object Timeout extends Command
  case object Ping extends Command
  case object Pong extends Event

  sealed trait Event
  case object Done extends Event

  // A no-op interceptor that deepens the behavior stack so the message
  // passes through InterceptorImpl.receive — the exact code path where
  // msg.getClass throws NPE on a null message.
  private def noopInterceptor[T: ClassTag] = new BehaviorInterceptor[T, T] {
    override def aroundReceive(
        ctx: TypedActorContext[T],
        msg: T,
        target: BehaviorInterceptor.ReceiveTarget[T]): Behavior[T] =
      target(ctx, msg)
  }

  "A typed actor with receive timeout" must {

    // Regression test for #3084: cancelReceiveTimeout() sets receiveTimeoutMsg to
    // null, but a classic ReceiveTimeout already enqueued in the mailbox cannot be
    // retracted. Before the fix the stale ReceiveTimeout was forwarded to the typed
    // behavior stack as a null message, causing NPE in InterceptorImpl.receive.
    "silently discard a stale ReceiveTimeout after cancelReceiveTimeout" in {
      val probe = TestProbe[Event]()

      def behavior: Behavior[Command] =
        Behaviors.setup { context =>
          // Wrap in an interceptor so the message traverses InterceptorImpl.receive,
          // the exact location where msg.getClass throws NPE on a null message.
          Behaviors.intercept[Command, Command](() => noopInterceptor[Command]) {
            Behaviors.receiveMessage {
              case SetAndCancelTimeout =>
                // Set a very long timeout (won't actually fire), then immediately
                // cancel it. This leaves receiveTimeoutMsg as null.
                context.setReceiveTimeout(1.hour, Timeout)
                context.cancelReceiveTimeout()
                probe.ref ! Done
                Behaviors.same

              case Timeout =>
                // Should never reach here: the timeout was cancelled and we send
                // the classic ReceiveTimeout ourselves to simulate the stale case.
                Behaviors.unhandled

              case Ping =>
                probe.ref ! Pong
                Behaviors.same
            }
          }
        }

      val ref = spawn(behavior)

      // Step 1: actor sets and cancels the receive timeout → receiveTimeoutMsg is now null
      ref ! SetAndCancelTimeout
      probe.expectMessage(Done)

      // Step 2: simulate a stale classic ReceiveTimeout arriving in the mailbox.
      // This is what happens when the scheduler fires ReceiveTimeout before
      // cancelReceiveTimeout() is processed but the actor dequeues them in the
      // opposite order.
      ref.toClassic ! pekko.actor.ReceiveTimeout

      // Step 3: verify the actor is still alive and responsive (no NPE crash).
      ref ! Ping
      probe.expectMessage(Pong)
    }
  }
}
