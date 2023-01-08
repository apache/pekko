/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed

import scala.concurrent.Await
import scala.concurrent.duration._

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter.TypedActorSystemOps
import pekko.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import pekko.persistence.typed.scaladsl.EventSourcedBehavior.CommandHandler
import pekko.testkit.TestLatch

object ManyRecoveriesSpec {

  sealed case class Cmd(s: String)

  final case class Evt(s: String)

  def persistentBehavior(
      name: String,
      probe: TestProbe[String],
      latch: Option[TestLatch]): EventSourcedBehavior[Cmd, Evt, String] =
    EventSourcedBehavior[Cmd, Evt, String](
      persistenceId = PersistenceId.ofUniqueId(name),
      emptyState = "",
      commandHandler = CommandHandler.command {
        case Cmd(s) => Effect.persist(Evt(s)).thenRun(_ => probe.ref ! s"$name-$s")
      },
      eventHandler = {
        case (state, _) => latch.foreach(Await.ready(_, 10.seconds)); state
      })

  def forwardBehavior(sender: TestProbe[String]): Behaviors.Receive[Int] =
    Behaviors.receiveMessagePartial[Int] {
      case value =>
        sender.ref ! value.toString
        Behaviors.same
    }

  def forN(n: Int)(mapper: Int => String): Set[String] =
    (1 to n).map(mapper).toSet
}

class ManyRecoveriesSpec extends ScalaTestWithActorTestKit(s"""
    pekko.actor.default-dispatcher {
      type = Dispatcher
      executor = "thread-pool-executor"
      thread-pool-executor {
        fixed-pool-size = 5
      }
    }
    pekko.persistence.max-concurrent-recoveries = 3
    pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    """) with AnyWordSpecLike with LogCapturing {

  import ManyRecoveriesSpec._

  "Many persistent actors" must {
    "be able to recover without overloading" in {
      val probe = TestProbe[String]()
      (1 to 100).foreach { n =>
        val name = s"a$n"
        spawn(persistentBehavior(s"a$n", probe, latch = None), name) ! Cmd("A")
        probe.expectMessage(s"a$n-A")
      }

      // this would starve (block) all threads without max-concurrent-recoveries
      val latch = TestLatch()(system.toClassic)
      (1 to 100).foreach { n =>
        spawn(persistentBehavior(s"a$n", probe, Some(latch))) ! Cmd("B")
      }
      // this should be able to progress even though above is blocking,
      // 2 remaining non-blocked threads
      (1 to 10).foreach { n =>
        spawn(forwardBehavior(probe)) ! n
        probe.expectMessage(n.toString)
      }

      latch.countDown()

      forN(100)(_ => probe.receiveMessage()) should
      be(forN(100)(i => s"a$i-B"))
    }
  }
}
