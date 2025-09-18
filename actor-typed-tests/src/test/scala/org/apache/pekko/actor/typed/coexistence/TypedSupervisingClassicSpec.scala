/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.coexistence
import org.apache.pekko
import pekko.{ actor => classic }
import pekko.actor.Actor
import pekko.actor.testkit.typed.TestException
import pekko.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.typed.ActorRef
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter._

import org.scalatest.wordspec.AnyWordSpecLike

object TypedSupervisingClassicSpec {

  sealed trait Protocol
  final case class SpawnClassicActor(props: classic.Props, replyTo: ActorRef[SpawnedClassicActor]) extends Protocol
  final case class SpawnedClassicActor(ref: classic.ActorRef)

  def classicActorOf() = Behaviors.receive[Protocol] {
    case (ctx, SpawnClassicActor(props, replyTo)) =>
      replyTo ! SpawnedClassicActor(ctx.actorOf(props))
      Behaviors.same
  }

  class CLassicActor(lifecycleProbe: ActorRef[String]) extends Actor {
    override def receive: Receive = {
      case "throw" => throw TestException("oh dear")
    }

    override def postStop(): Unit = {
      lifecycleProbe ! "postStop"
    }

    override def preStart(): Unit = {
      lifecycleProbe ! "preStart"
    }
  }

}

class TypedSupervisingClassicSpec extends ScalaTestWithActorTestKit("""
    pekko.loglevel = INFO
  """.stripMargin) with AnyWordSpecLike with LogCapturing {
  import TypedSupervisingClassicSpec._

  "Typed supervising classic" should {
    "default to restart" in {
      val ref: ActorRef[Protocol] = spawn(classicActorOf())
      val lifecycleProbe = TestProbe[String]()
      val probe = TestProbe[SpawnedClassicActor]()
      ref ! SpawnClassicActor(classic.Props(new CLassicActor(lifecycleProbe.ref)), probe.ref)
      val spawnedClassic = probe.expectMessageType[SpawnedClassicActor].ref
      lifecycleProbe.expectMessage("preStart")
      spawnedClassic ! "throw"
      lifecycleProbe.expectMessage("postStop")
      // should be restarted because it is a classic actor
      lifecycleProbe.expectMessage("preStart")
    }
  }

}
