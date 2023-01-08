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

package org.apache.pekko.actor.typed.scaladsl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.BootstrapSetup
import pekko.actor.setup.ActorSystemSetup
import pekko.actor.testkit.typed.scaladsl.ActorTestKit
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.LoggingTestKit
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.Props
import pekko.actor.typed.SpawnProtocol

object DispatcherSelectorSpec {
  val config = ConfigFactory.parseString("""
      ping-pong-dispatcher {
        executor = thread-pool-executor
        type = PinnedDispatcher
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

class DispatcherSelectorSpec(config: Config)
    extends ScalaTestWithActorTestKit(config)
    with AnyWordSpecLike
    with LogCapturing {
  import DispatcherSelectorSpec.PingPong
  import DispatcherSelectorSpec.PingPong._

  def this() = this(DispatcherSelectorSpec.config)

  "DispatcherSelector" must {

    "select dispatcher from config" in {
      val probe = createTestProbe[Pong]()
      val pingPong = spawn(PingPong(), Props.empty.withDispatcherFromConfig("ping-pong-dispatcher"))
      pingPong ! Ping(probe.ref)

      val response = probe.receiveMessage()
      response.threadName should startWith("DispatcherSelectorSpec-ping-pong-dispatcher")
    }

    "detect unknown dispatcher from config" in {
      val probe = createTestProbe[Pong]()
      LoggingTestKit.error("Spawn failed").expect {
        val ref = spawn(PingPong(), Props.empty.withDispatcherFromConfig("unknown"))
        probe.expectTerminated(ref)
      }
    }

    "select same dispatcher as parent" in {
      val parent = spawn(SpawnProtocol(), Props.empty.withDispatcherFromConfig("ping-pong-dispatcher"))
      val childProbe = createTestProbe[ActorRef[Ping]]()
      parent ! SpawnProtocol.Spawn(PingPong(), "child", Props.empty.withDispatcherSameAsParent, childProbe.ref)

      val probe = createTestProbe[Pong]()
      val child = childProbe.receiveMessage()
      child ! Ping(probe.ref)

      val response = probe.receiveMessage()
      response.threadName should startWith("DispatcherSelectorSpec-ping-pong-dispatcher")
    }

    "select same dispatcher as parent, several levels" in {
      val grandParent = spawn(SpawnProtocol(), Props.empty.withDispatcherFromConfig("ping-pong-dispatcher"))
      val parentProbe = createTestProbe[ActorRef[SpawnProtocol.Spawn[Ping]]]()
      grandParent ! SpawnProtocol.Spawn(
        SpawnProtocol(),
        "parent",
        Props.empty.withDispatcherSameAsParent,
        parentProbe.ref)

      val childProbe = createTestProbe[ActorRef[Ping]]()
      grandParent ! SpawnProtocol.Spawn(PingPong(), "child", Props.empty.withDispatcherSameAsParent, childProbe.ref)

      val probe = createTestProbe[Pong]()
      val child = childProbe.receiveMessage()
      child ! Ping(probe.ref)

      val response = probe.receiveMessage()
      response.threadName should startWith("DispatcherSelectorSpec-ping-pong-dispatcher")
    }

    "use default dispatcher if selecting parent dispatcher for user guardian" in {
      val sys = ActorSystem(
        PingPong(),
        "DispatcherSelectorSpec2",
        ActorSystemSetup.create(BootstrapSetup()),
        Props.empty.withDispatcherSameAsParent)
      try {
        val probe = TestProbe[Pong]()(sys)
        sys ! Ping(probe.ref)

        val response = probe.receiveMessage()
        response.threadName should startWith("DispatcherSelectorSpec2-pekko.actor.default-dispatcher")
      } finally {
        ActorTestKit.shutdown(sys)
      }
    }

  }

}
