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

import scala.concurrent.duration._
import scala.runtime.BoxedUnit

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor._
import pekko.japi.function.Procedure
import pekko.testkit.{ EventFilter, ImplicitSender }
import pekko.testkit.TestEvent.Mute

object TimerPersistentActorSpec {

  def testProps(name: String): Props =
    Props(new TestPersistentActor(name))

  final case class Scheduled(msg: Any, replyTo: ActorRef)

  final case class AutoReceivedMessageWrapper(msg: AutoReceivedMessage)

  class TestPersistentActor(name: String) extends Timers with PersistentActor {

    override def persistenceId = name

    override def receiveRecover: Receive = {
      case _ =>
    }

    override def receiveCommand: Receive = {
      case Scheduled(msg, replyTo) =>
        replyTo ! msg
      case AutoReceivedMessageWrapper(_) =>
        timers.startSingleTimer("PoisonPill", PoisonPill, Duration.Zero)
      case msg =>
        timers.startSingleTimer("key", Scheduled(msg, sender()), Duration.Zero)
        persist(msg)(_ => ())
    }
  }

  // this should fail in constructor
  class WrongOrder extends PersistentActor with Timers {
    override def persistenceId = "notused"
    override def receiveRecover: Receive = {
      case _ =>
    }
    override def receiveCommand: Receive = {
      case _ => ()
    }
  }

  def testJavaProps(name: String): Props =
    Props(new JavaTestPersistentActor(name))

  class JavaTestPersistentActor(name: String) extends AbstractPersistentActorWithTimers {

    override def persistenceId: String = name

    override def createReceiveRecover(): AbstractActor.Receive =
      AbstractActor.emptyBehavior

    override def createReceive(): AbstractActor.Receive =
      new AbstractActor.Receive({
        case Scheduled(msg, replyTo) =>
          replyTo ! msg
          BoxedUnit.UNIT
        case msg =>
          timers.startSingleTimer("key", Scheduled(msg, sender()), Duration.Zero)
          persist(msg,
            new Procedure[Any] {
              override def apply(evt: Any): Unit = ()
            })
          BoxedUnit.UNIT
      })
  }

}

class TimerPersistentActorSpec extends PersistenceSpec(ConfigFactory.parseString(s"""
    pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    pekko.actor.warn-about-java-serializer-usage = off
  """)) with ImplicitSender {
  import TimerPersistentActorSpec._

  system.eventStream.publish(Mute(EventFilter[ActorInitializationException]()))

  "PersistentActor with Timer" must {
    "not discard timer msg due to stashing" in {
      val pa = system.actorOf(testProps("p1"))
      pa ! "msg1"
      expectMsg("msg1")
    }

    "not discard timer msg due to stashing for AbstractPersistentActorWithTimers" in {
      val pa = system.actorOf(testJavaProps("p2"))
      pa ! "msg2"
      expectMsg("msg2")
    }

    "reject wrong order of traits, PersistentActor with Timer" in {
      if (TraitOrder.canBeChecked) {
        val pa = system.actorOf(Props[WrongOrder]())
        watch(pa)
        expectTerminated(pa)
      }
    }

    "handle AutoReceivedMessage's automatically" in {
      val pa = system.actorOf(testProps("p3"))
      watch(pa)
      pa ! AutoReceivedMessageWrapper(PoisonPill)
      expectTerminated(pa)
    }

  }

}
