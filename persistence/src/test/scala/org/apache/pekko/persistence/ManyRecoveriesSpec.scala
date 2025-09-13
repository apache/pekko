/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor._
import pekko.testkit._

import com.typesafe.config.ConfigFactory

object ManyRecoveriesSpec {

  def testProps(name: String, latch: Option[TestLatch]): Props =
    Props(new TestPersistentActor(name, latch))

  final case class Cmd(s: String)
  final case class Evt(s: String)

  class TestPersistentActor(name: String, latch: Option[TestLatch]) extends PersistentActor {

    override def persistenceId = name

    override def receiveRecover: Receive = {
      case Evt(_) =>
        latch.foreach(Await.ready(_, 10.seconds))
    }
    override def receiveCommand: Receive = {
      case Cmd(s) =>
        persist(Evt(s)) { _ =>
          sender() ! s"$persistenceId-$s-$lastSequenceNr"
        }
      case "stop" =>
        context.stop(self)
    }
  }

}

class ManyRecoveriesSpec extends PersistenceSpec(ConfigFactory.parseString(s"""
    pekko.actor.default-dispatcher {
      type = Dispatcher
      executor = "thread-pool-executor"
      thread-pool-executor {
        fixed-pool-size = 5
      }
    }
    pekko.persistence.max-concurrent-recoveries = 3
    pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    pekko.actor.warn-about-java-serializer-usage = off
  """)) with ImplicitSender {
  import ManyRecoveriesSpec._

  "Many persistent actors" must {
    "be able to recovery without overloading" in {
      (1 to 100).foreach { n =>
        system.actorOf(testProps(s"a$n", latch = None)) ! Cmd("A")
        expectMsg(s"a$n-A-1")
      }

      // this would starve (block) all threads without max-concurrent-recoveries
      val latch = TestLatch()
      (1 to 100).foreach { n =>
        system.actorOf(testProps(s"a$n", Some(latch))) ! Cmd("B")
      }
      // this should be able to progress even though above is blocking,
      // 2 remaining non-blocked threads
      (1 to 10).foreach { n =>
        system.actorOf(TestActors.echoActorProps) ! n
        expectMsg(n)
      }

      latch.countDown()
      receiveN(100).toSet should ===((1 to 100).map(n => s"a$n-B-2").toSet)
    }
  }

}
