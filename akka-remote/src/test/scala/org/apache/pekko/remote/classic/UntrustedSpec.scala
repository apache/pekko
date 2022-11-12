/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.classic

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorIdentity
import pekko.actor.ActorRef
import pekko.actor.ActorSelection
import pekko.actor.ActorSystem
import pekko.actor.Deploy
import pekko.actor.ExtendedActorSystem
import pekko.actor.Identify
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.actor.RootActorPath
import pekko.actor.Terminated
import pekko.event.Logging
import pekko.testkit.AkkaSpec
import pekko.testkit.EventFilter
import pekko.testkit.ImplicitSender
import pekko.testkit.JavaSerializable
import pekko.testkit.TestEvent
import pekko.testkit.TestProbe

object UntrustedSpec {
  final case class IdentifyReq(path: String) extends JavaSerializable
  final case class StopChild(name: String) extends JavaSerializable

  class Receptionist(testActor: ActorRef) extends Actor {
    context.actorOf(Props(classOf[Child], testActor), "child1")
    context.actorOf(Props(classOf[Child], testActor), "child2")
    context.actorOf(Props(classOf[FakeUser], testActor), "user")

    def receive = {
      case IdentifyReq(path) => context.actorSelection(path).tell(Identify(None), sender())
      case StopChild(name)   => context.child(name).foreach(context.stop)
      case msg               => testActor.forward(msg)
    }
  }

  class Child(testActor: ActorRef) extends Actor {
    override def postStop(): Unit = {
      testActor ! s"${self.path.name} stopped"
    }
    def receive = {
      case msg => testActor.forward(msg)
    }
  }

  class FakeUser(testActor: ActorRef) extends Actor {
    context.actorOf(Props(classOf[Child], testActor), "receptionist")
    def receive = {
      case msg => testActor.forward(msg)
    }
  }

}

class UntrustedSpec extends AkkaSpec("""
akka.loglevel = DEBUG
akka.actor.provider = remote
akka.remote.artery.enabled = off
akka.remote.warn-about-direct-use = off
akka.remote.classic.untrusted-mode = on
akka.remote.classic.trusted-selection-paths = ["/user/receptionist", ]    
akka.remote.classic.netty.tcp.port = 0
akka.loglevel = DEBUG # test verifies debug
# test is using Java serialization and not priority to rewrite
akka.actor.allow-java-serialization = on
akka.actor.warn-about-java-serializer-usage = off
akka.actor.serialization-bindings {
  "org.apache.pekko.actor.Terminated" = java-test
}
""") with ImplicitSender {

  import UntrustedSpec._

  val client = ActorSystem(
    "UntrustedSpec-client",
    ConfigFactory.parseString("""
      akka.loglevel = DEBUG
      akka.actor.provider = remote
      akka.remote.artery.enabled = off
      akka.remote.warn-about-direct-use = off
      akka.remote.classic.netty.tcp.port = 0
      # test is using Java serialization and not priority to rewrite
      akka.actor.allow-java-serialization = off
      akka.actor.warn-about-java-serializer-usage = off
      akka.actor.serialization-bindings {
        "org.apache.pekko.actor.Terminated" = java-test
      }
  """))
  val address = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress

  val receptionist = system.actorOf(Props(classOf[Receptionist], testActor), "receptionist")

  lazy val remoteDaemon = {
    {
      val p = TestProbe()(client)
      client.actorSelection(RootActorPath(address) / receptionist.path.elements).tell(IdentifyReq("/remote"), p.ref)
      p.expectMsgType[ActorIdentity].ref.get
    }
  }

  lazy val target2 = {
    val p = TestProbe()(client)
    client.actorSelection(RootActorPath(address) / receptionist.path.elements).tell(IdentifyReq("child2"), p.ref)
    p.expectMsgType[ActorIdentity].ref.get
  }

  override def afterTermination(): Unit = {
    shutdown(client)
  }

  // need to enable debug log-level without actually printing those messages
  system.eventStream.publish(TestEvent.Mute(EventFilter.debug()))

  "UntrustedMode" must {

    "allow actor selection to configured allow list" in {
      val sel = client.actorSelection(RootActorPath(address) / receptionist.path.elements)
      sel ! "hello"
      expectMsg("hello")
    }

    "discard harmful messages to /remote" in {
      val logProbe = TestProbe()
      // but instead install our own listener
      system.eventStream.subscribe(system.actorOf(Props(new Actor {
          import Logging._
          def receive = {
            case d @ Debug(_, _, msg: String) if msg contains "dropping" => logProbe.ref ! d
            case _                                                       =>
          }
        }).withDeploy(Deploy.local), "debugSniffer"), classOf[Logging.Debug])

      remoteDaemon ! "hello"
      logProbe.expectMsgType[Logging.Debug]
    }

    "discard harmful messages to testActor" in {
      target2 ! Terminated(remoteDaemon)(existenceConfirmed = true, addressTerminated = false)
      target2 ! PoisonPill
      client.stop(target2)
      target2 ! "blech"
      expectMsg("blech")
    }

    "discard watch messages" in {
      client.actorOf(Props(new Actor {
        context.watch(target2)
        def receive = {
          case x => testActor.forward(x)
        }
      }).withDeploy(Deploy.local))
      receptionist ! StopChild("child2")
      expectMsg("child2 stopped")
      // no Terminated msg, since watch was discarded
      expectNoMessage(1.second)
    }

    "discard actor selection" in {
      val sel = client.actorSelection(RootActorPath(address) / testActor.path.elements)
      sel ! "hello"
      expectNoMessage(1.second)
    }

    "discard actor selection with non root anchor" in {
      val p = TestProbe()(client)
      client.actorSelection(RootActorPath(address) / receptionist.path.elements).tell(Identify(None), p.ref)
      val clientReceptionistRef = p.expectMsgType[ActorIdentity].ref.get

      val sel = ActorSelection(clientReceptionistRef, receptionist.path.toStringWithoutAddress)
      sel ! "hello"
      expectNoMessage(1.second)
    }

    "discard actor selection to child of matching allow list" in {
      val sel = client.actorSelection(RootActorPath(address) / receptionist.path.elements / "child1")
      sel ! "hello"
      expectNoMessage(1.second)
    }

    "discard actor selection with wildcard" in {
      val sel = client.actorSelection(RootActorPath(address) / receptionist.path.elements / "*")
      sel ! "hello"
      expectNoMessage(1.second)
    }

    "discard actor selection containing harmful message" in {
      val sel = client.actorSelection(RootActorPath(address) / receptionist.path.elements)
      sel ! PoisonPill
      expectNoMessage(1.second)
    }

  }

}
