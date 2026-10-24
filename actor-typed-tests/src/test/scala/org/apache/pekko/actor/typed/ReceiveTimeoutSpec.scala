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

import java.util.concurrent.CompletableFuture

import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.NotInfluenceReceiveTimeout
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ManualTime
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.pattern.StatusReply
import pekko.testkit.CallingThreadDispatcher

import org.scalatest.wordspec.AnyWordSpecLike

object ReceiveTimeoutSpec {
  sealed trait Command
  case object Start extends Command
  case object Timeout extends Command
  case object Activity extends Command
  case object Tick extends Command with NotInfluenceReceiveTimeout
  case object ChangeTimeout extends Command with NotInfluenceReceiveTimeout
  case object CancelTimeout extends Command with NotInfluenceReceiveTimeout
}

class ReceiveTimeoutSpec extends ScalaTestWithActorTestKit(ManualTime.config) with AnyWordSpecLike with LogCapturing {
  import ReceiveTimeoutSpec._

  private val manualTime = ManualTime()
  private val callingThread = DispatcherSelector.fromConfig(CallingThreadDispatcher.Id)

  // Each registration returns an action that delivers a response only when the test requests it.
  private val adapters: Seq[(String, (ActorContext[Command], Command) => (() => Unit))] = Seq(
    "Scala messageAdapter" -> { (context, message) =>
      val adapter = context.messageAdapter[String](_ => message)
      () => adapter ! "response"
    },
    "Java messageAdapter" -> { (context, message) =>
      val adapter = context.asJava.messageAdapter[String](classOf[String], _ => message)
      () => adapter ! "response"
    },
    "spawnMessageAdapter" -> { (context, message) =>
      val adapter = context.spawnMessageAdapter[String](_ => message)
      () => adapter ! "response"
    },
    "Scala ask" -> { (context, message) =>
      val requests = TestProbe[ActorRef[String]]()
      context.ask(requests.ref, identity[ActorRef[String]])(_ => message)
      () => requests.receiveMessage() ! "response"
    },
    "Java ask" -> { (context, message) =>
      val requests = TestProbe[ActorRef[String]]()
      context.asJava.ask[ActorRef[String], String](classOf[String], requests.ref, java.time.Duration.ofSeconds(10),
        ref => ref, (_, _) => message)
      () => requests.receiveMessage() ! "response"
    },
    "Scala askWithStatus" -> { (context, message) =>
      val requests = TestProbe[ActorRef[StatusReply[String]]]()
      context.askWithStatus(requests.ref, identity[ActorRef[StatusReply[String]]])(_ => message)
      () => requests.receiveMessage() ! StatusReply.Success("response")
    },
    "Java askWithStatus" -> { (context, message) =>
      val requests = TestProbe[ActorRef[StatusReply[String]]]()
      context.asJava.askWithStatus[ActorRef[StatusReply[String]], String](
        classOf[String], requests.ref, java.time.Duration.ofSeconds(10), ref => ref, (_, _) => message)
      () => requests.receiveMessage() ! StatusReply.Success("response")
    },
    "Scala pipeToSelf" -> { (context, message) =>
      val promise = Promise[String]()
      context.pipeToSelf(promise.future)(_ => message)
      () => { promise.success("response"); () }
    },
    "Java pipeToSelf" -> { (context, message) =>
      val future = new CompletableFuture[String]()
      context.asJava.pipeToSelf(future, (_: String, _: Throwable) => message)
      () => { future.complete("response"); () }
    })

  "A typed actor with receive timeout" must {
    adapters.foreach { case (name, register) =>
      Seq(Tick, Activity).foreach { message =>
        s"${if (message == Tick) "preserve" else "reset"} the timeout for $message adapted by $name" in {
          val received = TestProbe[Command]()
          val deliver = TestProbe[() => Unit]()
          val ref = spawn(
            Behaviors.setup[Command] { context =>
              deliver.ref ! register(context, message)
              Behaviors.receiveMessage {
                case Start =>
                  context.setReceiveTimeout(1.second, Timeout)
                  Behaviors.same
                case Timeout =>
                  received.ref ! Timeout
                  Behaviors.stopped
                case response =>
                  received.ref ! response
                  Behaviors.same
              }
            },
            callingThread)

          try {
            // Synchronous dispatch ensures timeout scheduling has finished before advancing time.
            ref ! Start
            manualTime.timePasses(600.millis)
            deliver.receiveMessage()()
            received.expectMessage(message)
            manualTime.timePasses(400.millis)
            if (message == Activity) {
              received.expectNoMessage()
              manualTime.timePasses(600.millis)
            }
            received.expectMessage(Timeout)
          } finally testKit.stop(ref)
        }
      }
    }

    Seq(ChangeTimeout, CancelTimeout).foreach { message =>
      s"honor $message while handling an adapted NotInfluenceReceiveTimeout message" in {
        val received = TestProbe[Command]()
        val adapters = TestProbe[ActorRef[String]]()
        val ref = spawn(
          Behaviors.setup[Command] { context =>
            adapters.ref ! context.messageAdapter[String](_ => message)
            Behaviors.receiveMessage {
              case Start =>
                context.setReceiveTimeout(1.second, Timeout)
                Behaviors.same
              case ChangeTimeout =>
                context.setReceiveTimeout(2.seconds, Timeout)
                Behaviors.same
              case CancelTimeout =>
                context.cancelReceiveTimeout()
                Behaviors.same
              case Timeout =>
                received.ref ! Timeout
                Behaviors.stopped
              case _ => Behaviors.unhandled
            }
          },
          callingThread)

        try {
          ref ! Start
          manualTime.timePasses(600.millis)
          adapters.receiveMessage() ! "response"
          manualTime.expectNoMessageFor(1.second, received)
          manualTime.timePasses(1.second)
          if (message == ChangeTimeout) received.expectMessage(Timeout)
          else received.expectNoMessage()
        } finally testKit.stop(ref)
      }
    }
  }
}
