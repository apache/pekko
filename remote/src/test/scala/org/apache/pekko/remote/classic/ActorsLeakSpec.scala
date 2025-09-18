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

package org.apache.pekko.remote.classic

import scala.annotation.nowarn
import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor._
import pekko.actor.dungeon.ChildrenContainer
import pekko.remote.{ AddressUidExtension, RARP }
import pekko.remote.transport.ThrottlerTransportAdapter.ForceDisassociate
import pekko.testkit._
import pekko.testkit.TestActors.EchoActor

import com.typesafe.config.ConfigFactory

object ActorsLeakSpec {

  val config = ConfigFactory.parseString("""
       pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
       pekko.actor.provider = remote
       pekko.remote.artery.enabled = false
       pekko.remote.classic.netty.tcp.applied-adapters = ["trttl"]
       #pekko.remote.log-lifecycle-events = on
       pekko.remote.classic.transport-failure-detector.heartbeat-interval = 1 s
       pekko.remote.classic.transport-failure-detector.acceptable-heartbeat-pause = 3 s
       pekko.remote.classic.quarantine-after-silence = 3 s
       pekko.remote.use-unsafe-remote-features-outside-cluster = on
       pekko.test.filter-leeway = 12 s
       pekko.test.timefactor = 2
       # test is using Java serialization and not priority to rewrite
       pekko.actor.allow-java-serialization = on
       pekko.actor.warn-about-java-serializer-usage = off
      """)

  def collectLiveActors(root: Option[ActorRef]): immutable.Seq[ActorRef] = {

    def recurse(node: ActorRef): List[ActorRef] = {
      val children: List[ActorRef] = node match {
        case wc: ActorRefWithCell =>
          val cell = wc.underlying

          cell.childrenRefs match {
            case ChildrenContainer.TerminatingChildrenContainer(_, _, _)                                  => Nil
            case ChildrenContainer.TerminatedChildrenContainer | ChildrenContainer.EmptyChildrenContainer => Nil
            case _: ChildrenContainer.NormalChildrenContainer                                             => cell.childrenRefs.children.toList
            case _                                                                                        => Nil
          }
        case _ => Nil
      }

      node :: children.flatMap(recurse)
    }

    root match {
      case Some(node) => recurse(node)
      case None       => immutable.Seq.empty
    }
  }

  class StoppableActor extends Actor {
    override def receive = {
      case "stop" => context.stop(self)
    }
  }

}

class ActorsLeakSpec extends PekkoSpec(ActorsLeakSpec.config) with ImplicitSender {
  import ActorsLeakSpec._

  "Remoting" must {

    "not leak actors" in {
      system.actorOf(Props[EchoActor](), "echo")
      val echoPath = RootActorPath(RARP(system).provider.getDefaultAddress) / "user" / "echo"

      val targets = List("/system/endpointManager", "/system/transports").map { path =>
        system.actorSelection(path) ! Identify(0)
        expectMsgType[ActorIdentity].ref
      }

      val initialActors = targets.flatMap(collectLiveActors).toSet

      // Clean shutdown case
      for (_ <- 1 to 3) {

        val remoteSystem =
          ActorSystem(
            "remote",
            ConfigFactory.parseString("pekko.remote.classic.netty.tcp.port = 0").withFallback(config))

        try {
          val probe = TestProbe()(remoteSystem)

          remoteSystem.actorSelection(echoPath).tell(Identify(1), probe.ref)
          probe.expectMsgType[ActorIdentity].ref.nonEmpty should be(true)

        } finally {
          remoteSystem.terminate()
        }

        Await.result(remoteSystem.whenTerminated, 10.seconds)
      }

      // Quarantine an old incarnation case
      for (_ <- 1 to 3) {
        // always use the same address
        val remoteSystem =
          ActorSystem(
            "remote",
            ConfigFactory.parseString("""
                pekko.remote.artery.enabled = false
                pekko.remote.classic.netty.tcp.port = 7356
              """.stripMargin).withFallback(config))

        try {
          val remoteAddress = RARP(remoteSystem).provider.getDefaultAddress

          remoteSystem.actorOf(Props[StoppableActor](), "stoppable")

          // the message from remote to local will cause inbound connection established
          val probe = TestProbe()(remoteSystem)
          remoteSystem.actorSelection(echoPath).tell(Identify(1), probe.ref)
          probe.expectMsgType[ActorIdentity].ref.nonEmpty should be(true)

          val beforeQuarantineActors = targets.flatMap(collectLiveActors).toSet

          // it must not quarantine the current connection
          @nowarn
          val addressUid = AddressUidExtension(remoteSystem).addressUid + 1
          RARP(system).provider.transport.quarantine(remoteAddress, Some(addressUid), "test")

          // the message from local to remote should reuse passive inbound connection
          system.actorSelection(RootActorPath(remoteAddress) / "user" / "stoppable") ! Identify(1)
          expectMsgType[ActorIdentity].ref.nonEmpty should be(true)

          val afterQuarantineActors = targets.flatMap(collectLiveActors).toSet

          assertResult(beforeQuarantineActors)(afterQuarantineActors)

        } finally {
          remoteSystem.terminate()
        }

        Await.result(remoteSystem.whenTerminated, 10.seconds)

      }

      // Missing SHUTDOWN case
      for (_ <- 1 to 3) {

        val remoteSystem =
          ActorSystem(
            "remote",
            ConfigFactory.parseString("""
                 pekko.remote.artery.enabled = off
                 pekko.remote.classic.netty.tcp.port = 0
              """.stripMargin).withFallback(config))
        val remoteAddress = RARP(remoteSystem).provider.getDefaultAddress

        try {
          val probe = TestProbe()(remoteSystem)

          remoteSystem.actorSelection(echoPath).tell(Identify(1), probe.ref)
          probe.expectMsgType[ActorIdentity].ref.nonEmpty should be(true)

          // This will make sure that no SHUTDOWN message gets through
          Await.result(RARP(system).provider.transport.managementCommand(ForceDisassociate(remoteAddress)), 3.seconds)

        } finally {
          remoteSystem.terminate()
        }

        EventFilter
          .warning(start = s"Association with remote system [$remoteAddress] has failed", occurrences = 1)
          .intercept {
            Await.result(remoteSystem.whenTerminated, 10.seconds)
          }
      }

      // Remote idle for too long case
      val remoteSystem =
        ActorSystem("remote", ConfigFactory.parseString("pekko.remote.classic.netty.tcp.port = 0").withFallback(config))
      val remoteAddress = RARP(remoteSystem).provider.getDefaultAddress

      remoteSystem.actorOf(Props[StoppableActor](), "stoppable")

      try {
        val probe = TestProbe()(remoteSystem)

        remoteSystem.actorSelection(echoPath).tell(Identify(1), probe.ref)
        probe.expectMsgType[ActorIdentity].ref.nonEmpty should be(true)

        // Watch a remote actor - this results in system message traffic
        system.actorSelection(RootActorPath(remoteAddress) / "user" / "stoppable") ! Identify(1)
        val remoteActor = expectMsgType[ActorIdentity].ref.get
        watch(remoteActor)
        remoteActor ! "stop"
        expectTerminated(remoteActor)
        // All system messages has been acked now on this side

        // This will make sure that no SHUTDOWN message gets through
        Await.result(RARP(system).provider.transport.managementCommand(ForceDisassociate(remoteAddress)), 3.seconds)

      } finally {
        remoteSystem.terminate()
      }

      Await.result(remoteSystem.whenTerminated, 10.seconds)
      awaitAssert(assertResult(initialActors)(targets.flatMap(collectLiveActors).toSet), 10.seconds)
    }

  }

}
