/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.testkit

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.annotation.nowarn

import com.typesafe.config.ConfigFactory
import language.postfixOps

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.actor._
import pekko.actor.DeadLetter
import pekko.pattern.ask
import pekko.util.Timeout

@nowarn
class PekkoSpecSpec extends AnyWordSpec with Matchers {

  "A PekkoSpec" must {

    "warn about unhandled messages" in {
      implicit val system = ActorSystem("PekkoSpec0", PekkoSpec.testConf)
      try {
        val a = system.actorOf(Props.empty)
        EventFilter.warning(start = "unhandled message", occurrences = 1).intercept {
          a ! 42
        }
      } finally {
        TestKit.shutdownActorSystem(system)
      }
    }

    "terminate all actors" in {
      // verbose config just for demonstration purposes, please leave in in case of debugging
      import pekko.util.ccompat.JavaConverters._
      val conf = Map(
        "pekko.actor.debug.lifecycle" -> true,
        "pekko.actor.debug.event-stream" -> true,
        "pekko.loglevel" -> "DEBUG",
        "pekko.stdout-loglevel" -> "DEBUG")
      val localSystem = ActorSystem("PekkoSpec1", ConfigFactory.parseMap(conf.asJava).withFallback(PekkoSpec.testConf))
      var refs = Seq.empty[ActorRef]
      val spec = new PekkoSpec(localSystem) { refs = Seq(testActor, localSystem.actorOf(Props.empty, "name")) }
      refs.foreach(_.isTerminated should not be true)
      TestKit.shutdownActorSystem(localSystem)
      spec.awaitCond(refs.forall(_.isTerminated), 2.seconds)
    }

    "stop correctly when sending PoisonPill to rootGuardian" in {
      val system = ActorSystem("PekkoSpec2", PekkoSpec.testConf)
      new PekkoSpec(system) {}
      val latch = new TestLatch(1)(system)
      system.registerOnTermination(latch.countDown())

      system.actorSelection("/") ! PoisonPill

      Await.ready(latch, 2.seconds)
    }

    "enqueue unread messages from testActor to deadLetters" in {
      val system, otherSystem = ActorSystem("PekkoSpec3", PekkoSpec.testConf)

      try {
        var locker = Seq.empty[DeadLetter]
        implicit val timeout: Timeout = TestKitExtension(system).DefaultTimeout.duration.dilated(system)
        val davyJones = otherSystem.actorOf(Props(new Actor {
            def receive = {
              case m: DeadLetter => locker :+= m
              case "Die!"        => sender() ! "finally gone"; context.stop(self)
            }
          }), "davyJones")

        system.eventStream.subscribe(davyJones, classOf[DeadLetter])

        val probe = new TestProbe(system)
        probe.ref.tell(42, davyJones)
        /*
         * this will ensure that the message is actually received, otherwise it
         * may happen that the system.stop() suspends the testActor before it had
         * a chance to put the message into its private queue
         */
        probe.receiveWhile(1.second) {
          case null =>
        }

        val latch = new TestLatch(1)(system)
        system.registerOnTermination(latch.countDown())
        TestKit.shutdownActorSystem(system)
        Await.ready(latch, 2.seconds)
        Await.result(davyJones ? "Die!", timeout.duration) should ===("finally gone")

        // this will typically also contain log messages which were sent after the logger shutdown
        locker should contain(DeadLetter(42, davyJones, probe.ref))
      } finally {
        TestKit.shutdownActorSystem(system)
        TestKit.shutdownActorSystem(otherSystem)
      }
    }
  }
}
