/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed

import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.cluster.typed.ClusterSingleton
import pekko.cluster.typed.SingletonActor
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior

object ClusterSingletonPersistenceSpec {
  val config = ConfigFactory.parseString("""
      pekko.actor.provider = cluster
      pekko.remote.classic.netty.tcp.port = 0
      pekko.remote.artery.canonical.port = 0
      pekko.remote.artery.canonical.hostname = 127.0.0.1

      pekko.coordinated-shutdown.terminate-actor-system = off
      pekko.coordinated-shutdown.run-by-actor-system-terminate = off

      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    """.stripMargin)

  sealed trait Command
  final case class Add(s: String) extends Command
  final case class Get(replyTo: ActorRef[String]) extends Command
  private case object StopPlz extends Command

  val persistentActor: Behavior[Command] =
    EventSourcedBehavior[Command, String, String](
      persistenceId = PersistenceId.ofUniqueId("TheSingleton"),
      emptyState = "",
      commandHandler = (state, cmd) =>
        cmd match {
          case Add(s) => Effect.persist(s)
          case Get(replyTo) =>
            replyTo ! state
            Effect.none
          case StopPlz => Effect.stop()
        },
      eventHandler = (state, evt) => if (state.isEmpty) evt else state + "|" + evt)

}

class ClusterSingletonPersistenceSpec
    extends ScalaTestWithActorTestKit(ClusterSingletonPersistenceSpec.config)
    with AnyWordSpecLike
    with LogCapturing {
  import ClusterSingletonPersistenceSpec._

  import pekko.actor.typed.scaladsl.adapter._

  implicit val s: ActorSystem[Nothing] = system

  implicit val classicSystem: actor.ActorSystem = system.toClassic
  private val classicCluster = pekko.cluster.Cluster(classicSystem)

  "A typed cluster singleton with persistent actor" must {

    classicCluster.join(classicCluster.selfAddress)

    "start persistent actor" in {
      val ref = ClusterSingleton(system).init(SingletonActor(persistentActor, "singleton").withStopMessage(StopPlz))

      val p = TestProbe[String]()

      ref ! Add("a")
      ref ! Add("b")
      ref ! Add("c")
      ref ! Get(p.ref)
      p.expectMessage("a|b|c")
    }
  }
}
