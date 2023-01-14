/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.BehaviorInterceptor
import pekko.actor.typed.TypedActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.typed.PersistenceId
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.AtomicInteger
import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin

object DurableStateBehaviorInterceptorSpec {

  def conf: Config = PersistenceTestKitDurableStateStorePlugin.config.withFallback(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    """))

  def testBehavior(persistenceId: PersistenceId, probe: ActorRef[String]): Behavior[String] =
    Behaviors.setup { _ =>
      DurableStateBehavior[String, String](
        persistenceId,
        emptyState = "",
        commandHandler = (_, command) =>
          command match {
            case _ =>
              Effect.persist(command).thenRun(newState => probe ! newState)
          })
    }

}

class DurableStateBehaviorInterceptorSpec
    extends ScalaTestWithActorTestKit(DurableStateBehaviorInterceptorSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  import DurableStateBehaviorInterceptorSpec._

  val pidCounter = new AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId.ofUniqueId(s"c${pidCounter.incrementAndGet()})")

  "DurableStateBehavior interceptor" must {

    "be possible to combine with another interceptor" in {
      val probe = createTestProbe[String]()
      val pid = nextPid()

      val toUpper = new BehaviorInterceptor[String, String] {
        override def aroundReceive(
            ctx: TypedActorContext[String],
            msg: String,
            target: BehaviorInterceptor.ReceiveTarget[String]): Behavior[String] = {
          target(ctx, msg.toUpperCase())
        }
      }

      val ref = spawn(Behaviors.intercept(() => toUpper)(testBehavior(pid, probe.ref)))

      ref ! "a"
      ref ! "bc"
      probe.expectMessage("A")
      probe.expectMessage("BC")
    }

    "be possible to combine with transformMessages" in {
      val probe = createTestProbe[String]()
      val pid = nextPid()
      val ref = spawn(testBehavior(pid, probe.ref).transformMessages[String] {
        case s => s.toUpperCase()
      })

      ref ! "a"
      ref ! "bc"
      probe.expectMessage("A")
      probe.expectMessage("BC")
    }

    "be possible to combine with MDC" in {
      val probe = createTestProbe[String]()
      val pid = nextPid()
      val ref = spawn(Behaviors.setup[String] { _ =>
        Behaviors.withMdc(
          staticMdc = Map("pid" -> pid.toString),
          mdcForMessage = (msg: String) => Map("msg" -> msg.toUpperCase())) {
          testBehavior(pid, probe.ref)
        }
      })

      ref ! "a"
      ref ! "bc"
      probe.expectMessage("a")
      probe.expectMessage("bc")

    }
  }
}
