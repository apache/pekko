/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.classic

import scala.concurrent.duration._
import scala.annotation.nowarn
import com.typesafe.config._
import org.apache.pekko
import pekko.actor._
import pekko.remote.EndpointException
import pekko.remote.NotAllowedClassRemoteDeploymentAttemptException
import pekko.remote.transport._
import pekko.testkit._

// relies on test transport
object RemoteDeploymentAllowListSpec {

  class EchoAllowed extends Actor {
    var target: ActorRef = context.system.deadLetters

    def receive = {
      case ex: Exception => throw ex
      case x             => target = sender(); sender() ! x
    }

    override def preStart(): Unit = {}
    override def preRestart(cause: Throwable, msg: Option[Any]): Unit = {
      target ! "preRestart"
    }
    override def postRestart(cause: Throwable): Unit = {}
    override def postStop(): Unit = {
      target ! "postStop"
    }
  }

  class EchoNotAllowed extends Actor {
    var target: ActorRef = context.system.deadLetters

    def receive = {
      case ex: Exception => throw ex
      case x             => target = sender(); sender() ! x
    }

    override def preStart(): Unit = {}
    override def preRestart(cause: Throwable, msg: Option[Any]): Unit = {
      target ! "preRestart"
    }
    override def postRestart(cause: Throwable): Unit = {}
    override def postStop(): Unit = {
      target ! "postStop"
    }
  }

  val cfg: Config = ConfigFactory.parseString(s"""
    pekko {
      actor.provider = remote
      
      
      remote {
        use-unsafe-remote-features-outside-cluster = on
        classic.enabled-transports = [
          "pekko.remote.test",
          "pekko.remote.classic.netty.tcp"
        ]
        
        classic {
          netty.tcp = {
            port = 0
            hostname = "localhost"
          }
        }
        
        artery {
          enabled = off
        }
      
        test {
          transport-class = "org.apache.pekko.remote.transport.TestTransport"
          applied-adapters = []
          registry-key = aX33k0jWKg
          local-address = "test://RemoteDeploymentAllowListSpec@localhost:12345"
          maximum-payload-bytes = 32000 bytes
          scheme-identifier = test
        }
      }

      actor.deployment {
        /blub.remote = "akka.test://remote-sys@localhost:12346"
        /danger-mouse.remote = "akka.test://remote-sys@localhost:12346"
      }
    }
    # test is using Java serialization and not priority to rewrite
    pekko.actor.allow-java-serialization = on
    pekko.actor.warn-about-java-serializer-usage = off
  """)

  def muteSystem(system: ActorSystem): Unit = {
    system.eventStream.publish(
      TestEvent.Mute(
        EventFilter.error(start = "AssociationError"),
        EventFilter.warning(start = "AssociationError"),
        EventFilter.warning(pattern = "received dead letter.*")))
  }
}

@nowarn("msg=deprecated")
class RemoteDeploymentAllowListSpec
    extends PekkoSpec(RemoteDeploymentAllowListSpec.cfg)
    with ImplicitSender
    with DefaultTimeout {

  import RemoteDeploymentAllowListSpec._

  val conf =
    ConfigFactory.parseString("""
      pekko.remote.test {
        local-address = "test://remote-sys@localhost:12346"
        maximum-payload-bytes = 48000 bytes
      }

      //#allow-list-config
      pekko.remote.deployment {
        enable-allow-list = on
        
        allowed-actor-classes = [
          "NOT_ON_CLASSPATH", # verify we don't throw if a class not on classpath is listed here
          "org.apache.pekko.remote.classic.RemoteDeploymentAllowListSpec.EchoAllowed"
        ]
      }
      //#allow-list-config
    """).withFallback(system.settings.config).resolve()
  val remoteSystem = ActorSystem("remote-sys", conf)

  override def atStartup() = {
    muteSystem(system)
    remoteSystem.eventStream.publish(
      TestEvent.Mute(
        EventFilter[EndpointException](),
        EventFilter.error(start = "AssociationError"),
        EventFilter.warning(pattern = "received dead letter.*(InboundPayload|Disassociate|HandleListener)")))
  }

  override def afterTermination(): Unit = {
    shutdown(remoteSystem)
    AssociationRegistry.clear()
  }

  "RemoteDeployment Allow List" must {

    "allow deploying Echo actor (included in allow list)" in {
      val r = system.actorOf(Props[EchoAllowed](), "blub")
      r.path.toString should ===(
        s"akka.test://remote-sys@localhost:12346/remote/akka.test/${getClass.getSimpleName}@localhost:12345/user/blub")
      r ! 42
      expectMsg(42)
      EventFilter[Exception]("crash", occurrences = 1).intercept {
        r ! new Exception("crash")
      }
      expectMsg("preRestart")
      r ! 42
      expectMsg(42)
      system.stop(r)
      expectMsg("postStop")
    }

    "not deploy actor not listed in allow list" in {
      EventFilter
        .warning(start = "received dead letter", occurrences = 1)
        .intercept {
          EventFilter[NotAllowedClassRemoteDeploymentAttemptException](occurrences = 1).intercept {
            val r = system.actorOf(Props[EchoNotAllowed](), "danger-mouse")
            r.path.toString should ===(
              s"akka.test://remote-sys@localhost:12346/remote/akka.test/${getClass.getSimpleName}@localhost:12345/user/danger-mouse")
            r ! 42
            expectNoMessage(1.second)
            system.stop(r)
          }(remoteSystem)
        }(remoteSystem)
    }
  }
}
