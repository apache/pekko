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

package org.apache.pekko.actor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import org.apache.pekko
import pekko.ConfigurationException
import pekko.actor.setup.ActorSystemSetup
import pekko.dispatch.{ Dispatchers, ExecutionContexts }
import pekko.testkit.{ ImplicitSender, PekkoSpec, TestActors, TestProbe }

import com.typesafe.config.ConfigFactory

object ActorSystemDispatchersSpec {

  class SnitchingExecutionContext(testActor: ActorRef, underlying: ExecutionContext) extends ExecutionContext {

    def execute(runnable: Runnable): Unit = {
      testActor ! "called"
      underlying.execute(runnable)
    }

    def reportFailure(t: Throwable): Unit = {
      testActor ! "failed"
      underlying.reportFailure(t)
    }
  }

}

class ActorSystemDispatchersSpec extends PekkoSpec(ConfigFactory.parseString("""
    dispatcher-loop-1 = "dispatcher-loop-2"
    dispatcher-loop-2 = "dispatcher-loop-1"
  """)) with ImplicitSender {

  import ActorSystemDispatchersSpec._

  "The ActorSystem" must {

    "work with a passed in ExecutionContext" in {
      val ecProbe = TestProbe()
      val ec = new SnitchingExecutionContext(ecProbe.ref, ExecutionContexts.global())

      val system2 = ActorSystem(name = "ActorSystemDispatchersSpec-passed-in-ec", defaultExecutionContext = Some(ec))

      try {
        val ref = system2.actorOf(Props(new Actor {
          def receive = {
            case "ping" => sender() ! "pong"
          }
        }))

        val probe = TestProbe()

        ref.tell("ping", probe.ref)

        ecProbe.expectMsg(1.second, "called")
        probe.expectMsg(1.second, "pong")
      } finally {
        shutdown(system2)
      }
    }

    "not use passed in ExecutionContext if executor is configured" in {
      val ecProbe = TestProbe()
      val ec = new SnitchingExecutionContext(ecProbe.ref, ExecutionContexts.global())

      val config = ConfigFactory.parseString("pekko.actor.default-dispatcher.executor = \"fork-join-executor\"")
      val system2 = ActorSystem(
        name = "ActorSystemDispatchersSpec-ec-configured",
        config = Some(config),
        defaultExecutionContext = Some(ec))

      try {
        val ref = system2.actorOf(TestActors.echoActorProps)
        val probe = TestProbe()

        ref.tell("ping", probe.ref)

        ecProbe.expectNoMessage()
        probe.expectMsg(1.second, "ping")
      } finally {
        shutdown(system2)
      }
    }

    def userGuardianDispatcher(system: ActorSystem): String = {
      val impl = system.asInstanceOf[ActorSystemImpl]
      impl.guardian.asInstanceOf[ActorRefWithCell].underlying.asInstanceOf[ActorCell].dispatcher.id
    }

    "provide a single place to override the internal dispatcher" in {
      val sys = ActorSystem(
        "ActorSystemDispatchersSpec-override-internal-disp",
        ConfigFactory.parseString("""
             pekko.actor.internal-dispatcher = pekko.actor.default-dispatcher
           """))
      try {
        // that the user guardian runs on the overridden dispatcher instead of internal
        // isn't really a guarantee any internal actor has been made running on the right one
        // but it's better than no test coverage at all
        userGuardianDispatcher(sys) should ===("pekko.actor.default-dispatcher")
      } finally {
        shutdown(sys)
      }
    }

    "provide internal execution context instance through BootstrapSetup" in {
      val ecProbe = TestProbe()
      val ec = new SnitchingExecutionContext(ecProbe.ref, ExecutionContexts.global())

      // using the default for internal dispatcher and passing a pre-existing execution context
      val system2 =
        ActorSystem(
          name = "ActorSystemDispatchersSpec-passed-in-ec-for-internal",
          config = Some(ConfigFactory.parseString("""
            pekko.actor.internal-dispatcher = pekko.actor.default-dispatcher
          """)),
          defaultExecutionContext = Some(ec))

      try {
        val ref = system2.actorOf(Props(new Actor {
          def receive = {
            case "ping" => sender() ! "pong"
          }
        }).withDispatcher(Dispatchers.InternalDispatcherId))

        val probe = TestProbe()

        ref.tell("ping", probe.ref)

        ecProbe.expectMsg(1.second, "called")
        probe.expectMsg(1.second, "pong")
      } finally {
        shutdown(system2)
      }
    }

    "use an internal dispatcher for the guardian by default" in {
      userGuardianDispatcher(system) should ===("pekko.actor.internal-dispatcher")
    }

    "use the default dispatcher by a user provided user guardian" in {
      val sys = new ActorSystemImpl(
        "ActorSystemDispatchersSpec-custom-user-guardian",
        ConfigFactory.defaultReference(),
        getClass.getClassLoader,
        None,
        Some(Props.empty),
        ActorSystemSetup.empty)
      sys.start()
      try {
        userGuardianDispatcher(sys) should ===("pekko.actor.default-dispatcher")
      } finally shutdown(sys)
    }

    "provide a good error on an dispatcher alias loop in the config" in {
      intercept[ConfigurationException] {
        system.dispatchers.lookup("dispatcher-loop-1")
      }
    }

  }

}
