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

package org.apache.pekko.cluster.ddata

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorSystem
import pekko.actor.Props
import pekko.actor.Stash
import pekko.testkit.ImplicitSender
import pekko.testkit.TestKit

object LocalConcurrencySpec {

  final case class Add(s: String)

  object Updater {
    val key = ORSetKey[String]("key")
  }

  class Updater extends Actor with Stash {

    implicit val selfUniqueAddress: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

    val replicator = DistributedData(context.system).replicator

    def receive = {
      case s: String =>
        val update = Replicator.Update(Updater.key, ORSet.empty[String], Replicator.WriteLocal)(_ :+ s)
        replicator ! update
    }
  }
}

class LocalConcurrencySpec(_system: ActorSystem)
    extends TestKit(_system)
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {
  import LocalConcurrencySpec._

  def this() =
    this(
      ActorSystem(
        "LocalConcurrencySpec",
        ConfigFactory.parseString("""
      pekko.actor.provider = "cluster"
      pekko.remote.classic.netty.tcp.port=0
      pekko.remote.artery.canonical.port = 0
      """)))

  override def afterAll(): Unit = {
    shutdown(system)
  }

  val replicator = DistributedData(system).replicator

  "Updates from same node" must {

    "be possible to do from two actors" in {
      val updater1 = system.actorOf(Props[Updater](), "updater1")
      val updater2 = system.actorOf(Props[Updater](), "updater2")

      val numMessages = 100
      for (n <- 1 to numMessages) {
        updater1 ! s"a$n"
        updater2 ! s"b$n"
      }

      val expected = ((1 to numMessages).map("a" + _) ++ (1 to numMessages).map("b" + _)).toSet
      awaitAssert {
        replicator ! Replicator.Get(Updater.key, Replicator.ReadLocal)
        val elements = expectMsgType[Replicator.GetSuccess[?]].get(Updater.key) match {
          case ORSet(e) => e
          case _        => fail()
        }
        elements should be(expected)
      }

    }

  }
}
