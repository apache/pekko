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

import java.io.NotSerializableException
import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal

import scala.annotation.nowarn
import com.typesafe.config._

import org.apache.pekko
import pekko.actor._
import pekko.event.AddressTerminatedTopic
import pekko.pattern.ask
import pekko.remote._
import pekko.remote.transport._
import pekko.remote.transport.AssociationHandle.{ HandleEvent, HandleEventListener }
import pekko.remote.transport.Transport.InvalidAssociationException
import pekko.testkit._
import pekko.testkit.SocketUtil.temporaryServerAddress
import pekko.util.ByteString

object RemotingSpec {

  final case class ActorSelReq(s: String)

  class Echo1 extends Actor {
    var target: ActorRef = context.system.deadLetters

    def receive = {
      case (_: Props, n: String) => sender() ! context.actorOf(Props[Echo1](), n)
      case ex: Exception         => throw ex
      case ActorSelReq(s)        => sender() ! context.actorSelection(s)
      case x                     => target = sender(); sender() ! x
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

  class Echo2 extends Actor {
    def receive = {
      case "ping"                => sender() ! (("pong", sender()))
      case a: ActorRef           => a ! (("ping", sender()))
      case ("ping", a: ActorRef) => sender() ! (("pong", a))
      case ("pong", a: ActorRef) => a ! (("pong", sender().path.toSerializationFormat))
    }
  }

  class Proxy(val one: ActorRef, val another: ActorRef) extends Actor {
    def receive = {
      case s if sender().path == one.path     => another ! s
      case s if sender().path == another.path => one ! s
    }
  }

  val cfg: Config = ConfigFactory.parseString(s"""
    common-ssl-settings {
      key-store = "${getClass.getClassLoader.getResource("keystore").getPath}"
      trust-store = "${getClass.getClassLoader.getResource("truststore").getPath}"
      key-store-password = "changeme"
      key-password = "changeme"
      trust-store-password = "changeme"
      protocol = "TLSv1.2"
      enabled-algorithms = [TLS_RSA_WITH_AES_128_CBC_SHA, TLS_DHE_RSA_WITH_AES_256_GCM_SHA384]
    }

    common-netty-settings {
      port = 0
      hostname = "localhost"
    }

    pekko {
      actor.provider = remote
      # test is using Java serialization and not priority to rewrite
      actor.allow-java-serialization = on
      actor.warn-about-java-serializer-usage = off

      remote {
        use-unsafe-remote-features-outside-cluster = on
        artery.enabled = off
        classic {
          retry-gate-closed-for = 1 s
          log-remote-lifecycle-events = on

          enabled-transports = [
            "pekko.remote.classic.test",
            "pekko.remote.classic.netty.tcp",
            "pekko.remote.classic.netty.ssl"
          ]

          netty.tcp = $${common-netty-settings}
          netty.ssl = $${common-netty-settings}
          netty.ssl.security = $${common-ssl-settings}

          test {
            transport-class = "org.apache.pekko.remote.transport.TestTransport"
            applied-adapters = []
            registry-key = aX33k0jWKg
            local-address = "test://RemotingSpec@localhost:12345"
            maximum-payload-bytes = 32000 bytes
            scheme-identifier = test
          }
        }
      }

      actor.deployment {
        /blub.remote = "pekko.test://remote-sys@localhost:12346"
        /looker1/child.remote = "pekko.test://remote-sys@localhost:12346"
        /looker1/child/grandchild.remote = "pekko.test://RemotingSpec@localhost:12345"
        /looker2/child.remote = "pekko.test://remote-sys@localhost:12346"
        /looker2/child/grandchild.remote = "pekko.test://RemotingSpec@localhost:12345"
      }
    }
  """)

  def muteSystem(system: ActorSystem): Unit = {
    system.eventStream.publish(
      TestEvent.Mute(
        EventFilter.error(start = "AssociationError"),
        EventFilter.warning(start = "AssociationError"),
        EventFilter.warning(pattern = "received dead letter.*")))
  }
}

@nowarn
class RemotingSpec extends PekkoSpec(RemotingSpec.cfg) with ImplicitSender with DefaultTimeout {

  import RemotingSpec._

  val conf = ConfigFactory.parseString("""
      pekko.remote.artery.enabled = false
      pekko.remote.classic.test {
        local-address = "test://remote-sys@localhost:12346"
        maximum-payload-bytes = 48000 bytes
      }
    """).withFallback(system.settings.config).resolve()
  val remoteSystem = ActorSystem("remote-sys", conf)

  for ((name, proto) <- Seq("/gonk" -> "tcp", "/roghtaar" -> "ssl.tcp"))
    deploy(system, Deploy(name, scope = RemoteScope(getOtherAddress(remoteSystem, proto))))

  def getOtherAddress(sys: ActorSystem, proto: String) =
    sys.asInstanceOf[ExtendedActorSystem].provider.getExternalAddressFor(Address(s"pekko.$proto", "", "", 0)).get
  def port(sys: ActorSystem, proto: String) = getOtherAddress(sys, proto).port.get
  def deploy(sys: ActorSystem, d: Deploy): Unit = {
    sys.asInstanceOf[ExtendedActorSystem].provider.asInstanceOf[RemoteActorRefProvider].deployer.deploy(d)
  }

  val remote = remoteSystem.actorOf(Props[Echo2](), "echo")

  val here = RARP(system).provider.resolveActorRef("pekko.test://remote-sys@localhost:12346/user/echo")

  private def verifySend(msg: Any)(afterSend: => Unit): Unit = {
    val bigBounceId = s"bigBounce-${ThreadLocalRandom.current.nextInt()}"
    val bigBounceOther = remoteSystem.actorOf(Props(new Actor {
        def receive = {
          case x: Int => sender() ! byteStringOfSize(x)
          case x      => sender() ! x
        }
      }).withDeploy(Deploy.local), bigBounceId)
    val bigBounceHere =
      RARP(system).provider.resolveActorRef(s"pekko.test://remote-sys@localhost:12346/user/$bigBounceId")

    val eventForwarder = system.actorOf(Props(new Actor {
      def receive = {
        case x => testActor ! x
      }
    }).withDeploy(Deploy.local))
    system.eventStream.subscribe(eventForwarder, classOf[AssociationErrorEvent])
    system.eventStream.subscribe(eventForwarder, classOf[DisassociatedEvent])
    try {
      bigBounceHere ! msg
      afterSend
      expectNoMessage(500.millis.dilated)
    } finally {
      system.eventStream.unsubscribe(eventForwarder, classOf[AssociationErrorEvent])
      system.eventStream.unsubscribe(eventForwarder, classOf[DisassociatedEvent])
      eventForwarder ! PoisonPill
      bigBounceOther ! PoisonPill
    }
  }

  override def atStartup() = {
    muteSystem(system)
    remoteSystem.eventStream.publish(
      TestEvent.Mute(
        EventFilter[EndpointException](),
        EventFilter.error(start = "AssociationError"),
        EventFilter.warning(pattern = "received dead letter.*(InboundPayload|Disassociate|HandleListener)")))
  }

  private def byteStringOfSize(size: Int) = ByteString.fromArray(Array.fill(size)(42: Byte))

  val maxPayloadBytes = system.settings.config.getBytes("pekko.remote.classic.test.maximum-payload-bytes").toInt

  override def afterTermination(): Unit = {
    shutdown(remoteSystem)
    AssociationRegistry.clear()
  }

  "Remoting" must {

    "support remote look-ups" in {
      here ! "ping"
      expectMsg(("pong", testActor))
    }

    "send warning message for wrong address" in {
      filterEvents(EventFilter.warning(pattern = "Address is now gated for ", occurrences = 1)) {
        RARP(system).provider.resolveActorRef("pekko.test://nonexistingsystem@localhost:12346/user/echo") ! "ping"
      }
    }

    "support ask" in {
      Await.result(here ? "ping", timeout.duration) match {
        case ("pong", _: pekko.pattern.PromiseActorRef) => // good
        case m                                          => fail("" + m + " was not (pong, AskActorRef)")
      }
    }

    "send dead letters on remote if actor does not exist" in {
      EventFilter
        .warning(pattern = "dead.*buh", occurrences = 1)
        .intercept {
          RARP(system).provider.resolveActorRef("pekko.test://remote-sys@localhost:12346/does/not/exist") ! "buh"
        }(remoteSystem)
    }

    "not be exhausted by sending to broken connections" in {
      val tcpOnlyConfig = ConfigFactory
        .parseString("""pekko.remote.enabled-transports = ["pekko.remote.classic.netty.tcp"]""")
        .withFallback(remoteSystem.settings.config)
      val moreSystems = Vector.fill(5)(ActorSystem(remoteSystem.name, tcpOnlyConfig))
      moreSystems.foreach { sys =>
        sys.eventStream.publish(TestEvent
          .Mute(EventFilter[EndpointDisassociatedException](), EventFilter.warning(pattern = "received dead letter.*")))
        sys.actorOf(Props[Echo2](), name = "echo")
      }
      val moreRefs =
        moreSystems.map(sys => system.actorSelection(RootActorPath(getOtherAddress(sys, "tcp")) / "user" / "echo"))
      val aliveEcho = system.actorSelection(RootActorPath(getOtherAddress(remoteSystem, "tcp")) / "user" / "echo")
      val n = 100

      // first everything is up and running
      (1 to n).foreach { x =>
        aliveEcho ! "ping"
        moreRefs(x % moreSystems.size) ! "ping"
      }

      within(5.seconds) {
        receiveN(n * 2).foreach { reply =>
          reply should ===(("pong", testActor))
        }
      }

      // then we shutdown all but one system to simulate broken connections
      moreSystems.foreach { sys =>
        shutdown(sys)
      }

      (1 to n).foreach { x =>
        aliveEcho ! "ping"
        moreRefs(x % moreSystems.size) ! "ping"
      }

      // ping messages to aliveEcho should go through even though we use many different broken connections
      within(5.seconds) {
        receiveN(n).foreach { reply =>
          reply should ===(("pong", testActor))
        }
      }
    }

    "create and supervise children on remote node" in {
      val r = system.actorOf(Props[Echo1](), "blub")
      r.path.toString should ===(
        "pekko.test://remote-sys@localhost:12346/remote/pekko.test/RemotingSpec@localhost:12345/user/blub")
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

    "not send to remote re-created actor with same name" in {
      val echo = remoteSystem.actorOf(Props[Echo1](), "otherEcho1")
      echo ! 71
      expectMsg(71)
      echo ! PoisonPill
      expectMsg("postStop")
      echo ! 72
      expectNoMessage(1.second)

      val echo2 = remoteSystem.actorOf(Props[Echo1](), "otherEcho1")
      echo2 ! 73
      expectMsg(73)
      // msg to old ActorRef (different uid) should not get through
      echo2.path.uid should not be (echo.path.uid)
      echo ! 74
      expectNoMessage(1.second)

      remoteSystem.actorSelection("/user/otherEcho1") ! 75
      expectMsg(75)

      system.actorSelection("pekko.test://remote-sys@localhost:12346/user/otherEcho1") ! 76
      expectMsg(76)

      remoteSystem.actorSelection("/user/otherEcho1") ! 77
      expectMsg(77)

      system.actorSelection("pekko.test://remote-sys@localhost:12346/user/otherEcho1") ! 78
      expectMsg(78)
    }

    "select actors across node boundaries" in {
      val l = system.actorOf(Props(new Actor {
          def receive = {
            case (p: Props, n: String) => sender() ! context.actorOf(p, n)
            case ActorSelReq(s)        => sender() ! context.actorSelection(s)
          }
        }), "looker2")
      // child is configured to be deployed on remoteSystem
      l ! ((Props[Echo1](), "child"))
      val child = expectMsgType[ActorRef]
      // grandchild is configured to be deployed on RemotingSpec (system)
      child ! ((Props[Echo1](), "grandchild"))
      val grandchild = expectMsgType[ActorRef]
      grandchild.asInstanceOf[ActorRefScope].isLocal should ===(true)
      grandchild ! 53
      expectMsg(53)
      val mysel = system.actorSelection(system / "looker2" / "child" / "grandchild")
      mysel ! 54
      expectMsg(54)
      lastSender should ===(grandchild)
      (lastSender should be).theSameInstanceAs(grandchild)
      mysel ! Identify(mysel)
      val grandchild2 = expectMsgType[ActorIdentity].ref
      grandchild2 should ===(Some(grandchild))
      system.actorSelection("/user/looker2/child") ! Identify(None)
      expectMsgType[ActorIdentity].ref should ===(Some(child))
      l ! ActorSelReq("child/..")
      expectMsgType[ActorSelection] ! Identify(None)
      (expectMsgType[ActorIdentity].ref.get should be).theSameInstanceAs(l)
      system.actorSelection(system / "looker2" / "child") ! ActorSelReq("..")
      expectMsgType[ActorSelection] ! Identify(None)
      (expectMsgType[ActorIdentity].ref.get should be).theSameInstanceAs(l)

      grandchild ! ((Props[Echo1](), "grandgrandchild"))
      val grandgrandchild = expectMsgType[ActorRef]

      system.actorSelection("/user/looker2/child") ! Identify("idReq1")
      expectMsg(ActorIdentity("idReq1", Some(child)))
      system.actorSelection(child.path) ! Identify("idReq2")
      expectMsg(ActorIdentity("idReq2", Some(child)))
      system.actorSelection("/user/looker2/*") ! Identify("idReq3")
      expectMsg(ActorIdentity("idReq3", Some(child)))

      system.actorSelection("/user/looker2/child/grandchild") ! Identify("idReq4")
      expectMsg(ActorIdentity("idReq4", Some(grandchild)))
      system.actorSelection(child.path / "grandchild") ! Identify("idReq5")
      expectMsg(ActorIdentity("idReq5", Some(grandchild)))
      system.actorSelection("/user/looker2/*/grandchild") ! Identify("idReq6")
      expectMsg(ActorIdentity("idReq6", Some(grandchild)))
      system.actorSelection("/user/looker2/child/*") ! Identify("idReq7")
      expectMsg(ActorIdentity("idReq7", Some(grandchild)))
      system.actorSelection(child.path / "*") ! Identify("idReq8")
      expectMsg(ActorIdentity("idReq8", Some(grandchild)))

      system.actorSelection("/user/looker2/child/grandchild/grandgrandchild") ! Identify("idReq9")
      expectMsg(ActorIdentity("idReq9", Some(grandgrandchild)))
      system.actorSelection(child.path / "grandchild" / "grandgrandchild") ! Identify("idReq10")
      expectMsg(ActorIdentity("idReq10", Some(grandgrandchild)))
      system.actorSelection("/user/looker2/child/*/grandgrandchild") ! Identify("idReq11")
      expectMsg(ActorIdentity("idReq11", Some(grandgrandchild)))
      system.actorSelection("/user/looker2/child/*/*") ! Identify("idReq12")
      expectMsg(ActorIdentity("idReq12", Some(grandgrandchild)))
      system.actorSelection(child.path / "*" / "grandgrandchild") ! Identify("idReq13")
      expectMsg(ActorIdentity("idReq13", Some(grandgrandchild)))

      val sel1 = system.actorSelection("/user/looker2/child/grandchild/grandgrandchild")
      system.actorSelection(sel1.toSerializationFormat) ! Identify("idReq18")
      expectMsg(ActorIdentity("idReq18", Some(grandgrandchild)))

      child ! Identify("idReq14")
      expectMsg(ActorIdentity("idReq14", Some(child)))
      watch(child)
      child ! PoisonPill
      expectMsg("postStop")
      expectMsgType[Terminated].actor should ===(child)
      l ! ((Props[Echo1](), "child"))
      val child2 = expectMsgType[ActorRef]
      child2 ! Identify("idReq15")
      expectMsg(ActorIdentity("idReq15", Some(child2)))
      system.actorSelection(child.path) ! Identify("idReq16")
      expectMsg(ActorIdentity("idReq16", Some(child2)))
      child ! Identify("idReq17")
      expectMsg(ActorIdentity("idReq17", None))

      child2 ! 55
      expectMsg(55)
      // msg to old ActorRef (different uid) should not get through
      child2.path.uid should not be (child.path.uid)
      child ! 56
      expectNoMessage(1.second)
      system.actorSelection(system / "looker2" / "child") ! 57
      expectMsg(57)
    }

    "not fail ask across node boundaries" in within(5.seconds) {
      import system.dispatcher
      val f = for (_ <- 1 to 1000) yield (here ? "ping").mapTo[(String, ActorRef)]
      Await.result(Future.sequence(f), timeout.duration).map(_._1).toSet should ===(Set("pong"))
    }

    "be able to use multiple transports and use the appropriate one (TCP)" in {
      val r = system.actorOf(Props[Echo1](), "gonk")
      r.path.toString should ===(
        s"pekko.tcp://remote-sys@localhost:${port(remoteSystem, "tcp")}/remote/pekko.tcp/RemotingSpec@localhost:${port(
            system, "tcp")}/user/gonk")
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

    "be able to use multiple transports and use the appropriate one (SSL)" in {
      val r = system.actorOf(Props[Echo1](), "roghtaar")
      r.path.toString should ===(
        s"pekko.ssl.tcp://remote-sys@localhost:${port(remoteSystem,
            "ssl.tcp")}/remote/pekko.ssl.tcp/RemotingSpec@localhost:${port(system, "ssl.tcp")}/user/roghtaar")
      r ! 42
      expectMsg(10.seconds, 42)
      EventFilter[Exception]("crash", occurrences = 1).intercept {
        r ! new Exception("crash")
      }
      expectMsg("preRestart")
      r ! 42
      expectMsg(42)
      system.stop(r)
      expectMsg("postStop")
    }

    "drop unserializable messages" in {
      object Unserializable
      EventFilter[NotSerializableException](pattern = ".*No configured serialization.*", occurrences = 1).intercept {
        verifySend(Unserializable) {
          expectNoMessage(1.second) // No AssocitionErrorEvent should be published
        }
      }
    }

    "allow messages up to payload size" in {
      val maxProtocolOverhead = 500 // Make sure we're still under size after the message is serialized, etc
      val big = byteStringOfSize(maxPayloadBytes - maxProtocolOverhead)
      verifySend(big) {
        expectMsg(3.seconds, big)
      }
    }

    "drop sent messages over payload size" in {
      val droppedProbe = TestProbe()
      system.eventStream.subscribe(droppedProbe.ref, classOf[Dropped])
      val oversized = byteStringOfSize(maxPayloadBytes + 1)
      EventFilter[OversizedPayloadException](pattern = ".*Discarding oversized payload sent.*", occurrences = 1)
        .intercept {
          verifySend(oversized) {
            expectNoMessage(1.second) // No AssocitionErrorEvent should be published
          }
        }
      droppedProbe.expectMsgType[Dropped].message should ===(oversized)
    }

    "drop received messages over payload size" in {
      // Receiver should reply with a message of size maxPayload + 1, which will be dropped and an error logged
      EventFilter[OversizedPayloadException](pattern = ".*Discarding oversized payload received.*", occurrences = 1)
        .intercept {
          verifySend(maxPayloadBytes + 1) {
            expectNoMessage(1.second) // No AssocitionErrorEvent should be published
          }
        }
    }

    "be able to serialize a local actor ref from another actor system" in {
      val config = ConfigFactory
        .parseString(
          """
            pekko.remote.classic.enabled-transports = ["pekko.remote.classic.test", "pekko.remote.classic.netty.tcp"]
            pekko.remote.classic.test.local-address = "test://other-system@localhost:12347"
          """)
        .withFallback(remoteSystem.settings.config)
      val otherSystem = ActorSystem("other-system", config)
      try {
        val otherGuy = otherSystem.actorOf(Props[Echo2](), "other-guy")
        // check that we use the specified transport address instead of the default
        val otherGuyRemoteTcp = otherGuy.path.toSerializationFormatWithAddress(getOtherAddress(otherSystem, "tcp"))
        val remoteEchoHereTcp =
          RARP(system).provider
            .resolveActorRef(s"pekko.tcp://remote-sys@localhost:${port(remoteSystem, "tcp")}/user/echo")
        val proxyTcp = system.actorOf(Props(classOf[Proxy], remoteEchoHereTcp, testActor), "proxy-tcp")
        proxyTcp ! otherGuy
        expectMsg(3.seconds, ("pong", otherGuyRemoteTcp))
        // now check that we fall back to default when we haven't got a corresponding transport
        val otherGuyRemoteTest = otherGuy.path.toSerializationFormatWithAddress(getOtherAddress(otherSystem, "test"))
        val remoteEchoHereSsl =
          RARP(system).provider
            .resolveActorRef(s"pekko.ssl.tcp://remote-sys@localhost:${port(remoteSystem, "ssl.tcp")}/user/echo")
        val proxySsl = system.actorOf(Props(classOf[Proxy], remoteEchoHereSsl, testActor), "proxy-ssl")
        EventFilter
          .warning(start = "Error while resolving ActorRef", occurrences = 1)
          .intercept {
            proxySsl ! otherGuy
            expectMsg(3.seconds, ("pong", otherGuyRemoteTest))
          }(otherSystem)
      } finally {
        shutdown(otherSystem)
      }
    }

    "should not publish AddressTerminated even on InvalidAssociationExceptions" in {
      val localAddress = Address("pekko.test", "system1", "localhost", 1)
      val rawLocalAddress = localAddress.copy(protocol = "test")
      val remoteAddress = Address("pekko.test", "system2", "localhost", 2)

      val config = ConfigFactory.parseString(s"""
            pekko.remote.enabled-transports = ["pekko.remote.classic.test"]
            pekko.remote.retry-gate-closed-for = 5s

            pekko.remote.classic.test {
              registry-key = tFdVxq
              local-address = "test://${localAddress.system}@${localAddress.host.get}:${localAddress.port.get}"
            }
            """).withFallback(remoteSystem.settings.config)

      val thisSystem = ActorSystem("this-system", config)

      try {
        class HackyRef extends MinimalActorRef {
          @volatile var lastMsg: AnyRef = null

          override def provider: ActorRefProvider = RARP(thisSystem).provider

          override def path: ActorPath = thisSystem / "user" / "evilref"

          override def isTerminated: Boolean = false

          override def !(message: Any)(implicit sender: ActorRef): Unit = lastMsg = message.asInstanceOf[AnyRef]
        }

        val terminatedListener = new HackyRef

        // Set up all connection attempts to fail
        val registry = AssociationRegistry.get("tFdVxq")
        awaitCond(registry.transportsReady(rawLocalAddress))
        awaitCond {
          registry.transportFor(rawLocalAddress) match {
            case None => false
            case Some((testTransport, _)) =>
              testTransport.associateBehavior.pushError(new InvalidAssociationException("Test connection error"))
              true
          }
        }

        AddressTerminatedTopic(thisSystem).subscribe(terminatedListener)

        val probe = new TestProbe(thisSystem)
        val otherSelection =
          thisSystem.actorSelection(ActorPath.fromString(remoteAddress.toString + "/user/noonethere"))
        otherSelection.tell("ping", probe.ref)
        probe.expectNoMessage(1.second)

        terminatedListener.lastMsg should be(null)

      } finally shutdown(thisSystem)
    }

    "should stash inbound connections until UID is known for pending outbound" in {
      val localAddress = Address("pekko.test", "system1", "localhost", 1)
      val rawLocalAddress = localAddress.copy(protocol = "test")
      val remoteAddress = Address("pekko.test", "system2", "localhost", 2)
      val rawRemoteAddress = remoteAddress.copy(protocol = "test")

      val config = ConfigFactory.parseString(s"""
        pekko.remote.classic.enabled-transports = ["pekko.remote.classic.test"]
        pekko.remote.classic.retry-gate-closed-for = 5s
        pekko.remote.classic.log-remote-lifecycle-events = on

        pekko.remote.classic.test {
          registry-key = TRKAzR
          local-address = "test://${localAddress.system}@${localAddress.host.get}:${localAddress.port.get}"
        }
        """).withFallback(remoteSystem.settings.config)
      val thisSystem = ActorSystem("this-system", config)
      muteSystem(thisSystem)

      try {

        // Set up a mock remote system using the test transport
        val registry = AssociationRegistry.get("TRKAzR")
        val remoteTransport = new TestTransport(rawRemoteAddress, registry)
        val remoteTransportProbe = TestProbe()

        registry.registerTransport(
          remoteTransport,
          associationEventListenerFuture = Future.successful(new Transport.AssociationEventListener {
            override def notify(ev: Transport.AssociationEvent): Unit =
              remoteTransportProbe.ref ! ev
          }))

        // Hijack associations through the test transport
        awaitCond(registry.transportsReady(rawLocalAddress, rawRemoteAddress))
        val testTransport = registry.transportFor(rawLocalAddress).get._1
        testTransport.writeBehavior.pushConstant(true)

        // Force an outbound associate on the real system (which we will hijack)
        // we send no handshake packet, so this remains a pending connection
        val dummySelection =
          thisSystem.actorSelection(ActorPath.fromString(remoteAddress.toString + "/user/noonethere"))
        dummySelection.tell("ping", system.deadLetters)

        val remoteHandle = remoteTransportProbe.expectMsgType[Transport.InboundAssociation]
        remoteHandle.association.readHandlerPromise.success(new HandleEventListener {
          override def notify(ev: HandleEvent): Unit = ()
        })

        // Now we initiate an emulated inbound connection to the real system
        val inboundHandleProbe = TestProbe()
        val inboundHandle = Await.result(remoteTransport.associate(rawLocalAddress), 3.seconds)
        inboundHandle.readHandlerPromise.success(new AssociationHandle.HandleEventListener {
          override def notify(ev: HandleEvent): Unit = inboundHandleProbe.ref ! ev
        })

        awaitAssert {
          registry.getRemoteReadHandlerFor(inboundHandle.asInstanceOf[TestAssociationHandle]).get
        }

        val handshakePacket =
          PekkoPduProtobufCodec$.constructAssociate(HandshakeInfo(rawRemoteAddress, uid = 0))
        val brokenPacket = PekkoPduProtobufCodec$.constructPayload(ByteString(0, 1, 2, 3, 4, 5, 6))

        // Finish the inbound handshake so now it is handed up to Remoting
        inboundHandle.write(handshakePacket)
        // Now bork the connection with a malformed packet that can only signal an error if the Endpoint is already registered
        // but not while it is stashed
        inboundHandle.write(brokenPacket)

        // No disassociation now, the connection is still stashed
        inboundHandleProbe.expectNoMessage(1.second)

        // Finish the handshake for the outbound connection. This will unstash the inbound pending connection.
        remoteHandle.association.write(handshakePacket)

        inboundHandleProbe.expectMsgType[AssociationHandle.Disassociated]
      } finally shutdown(thisSystem)

    }

    "should properly quarantine stashed inbound connections" in {
      val localAddress = Address("pekko.test", "system1", "localhost", 1)
      val rawLocalAddress = localAddress.copy(protocol = "test")
      val remoteAddress = Address("pekko.test", "system2", "localhost", 2)
      val rawRemoteAddress = remoteAddress.copy(protocol = "test")
      val remoteUID = 16

      val config = ConfigFactory.parseString(s"""
        pekko.remote.classic.enabled-transports = ["pekko.remote.classic.test"]
        pekko.remote.classic.retry-gate-closed-for = 5s
        pekko.remote.classic.log-remote-lifecycle-events = on

        pekko.remote.classic.test {
          registry-key = JMeMndLLsw
          local-address = "test://${localAddress.system}@${localAddress.host.get}:${localAddress.port.get}"
        }
        """).withFallback(remoteSystem.settings.config)
      val thisSystem = ActorSystem("this-system", config)
      muteSystem(thisSystem)

      try {

        // Set up a mock remote system using the test transport
        val registry = AssociationRegistry.get("JMeMndLLsw")
        val remoteTransport = new TestTransport(rawRemoteAddress, registry)
        val remoteTransportProbe = TestProbe()

        registry.registerTransport(
          remoteTransport,
          associationEventListenerFuture = Future.successful(new Transport.AssociationEventListener {
            override def notify(ev: Transport.AssociationEvent): Unit =
              remoteTransportProbe.ref ! ev
          }))

        // Hijack associations through the test transport
        awaitCond(registry.transportsReady(rawLocalAddress, rawRemoteAddress))
        val testTransport = registry.transportFor(rawLocalAddress).get._1
        testTransport.writeBehavior.pushConstant(true)

        // Force an outbound associate on the real system (which we will hijack)
        // we send no handshake packet, so this remains a pending connection
        val dummySelection =
          thisSystem.actorSelection(ActorPath.fromString(remoteAddress.toString + "/user/noonethere"))
        dummySelection.tell("ping", system.deadLetters)

        val remoteHandle = remoteTransportProbe.expectMsgType[Transport.InboundAssociation]
        remoteHandle.association.readHandlerPromise.success(new HandleEventListener {
          override def notify(ev: HandleEvent): Unit = ()
        })

        // Now we initiate an emulated inbound connection to the real system
        val inboundHandleProbe = TestProbe()
        val inboundHandle = Await.result(remoteTransport.associate(rawLocalAddress), 3.seconds)
        inboundHandle.readHandlerPromise.success(new AssociationHandle.HandleEventListener {
          override def notify(ev: HandleEvent): Unit = inboundHandleProbe.ref ! ev
        })

        awaitAssert {
          registry.getRemoteReadHandlerFor(inboundHandle.asInstanceOf[TestAssociationHandle]).get
        }

        val handshakePacket =
          PekkoPduProtobufCodec$.constructAssociate(HandshakeInfo(rawRemoteAddress, uid = remoteUID))

        // Finish the inbound handshake so now it is handed up to Remoting
        inboundHandle.write(handshakePacket)

        // No disassociation now, the connection is still stashed
        inboundHandleProbe.expectNoMessage(1.second)

        // Quarantine unrelated connection
        RARP(thisSystem).provider.quarantine(remoteAddress, Some(-1), "test")
        inboundHandleProbe.expectNoMessage(1.second)

        // Quarantine the connection
        RARP(thisSystem).provider.quarantine(remoteAddress, Some(remoteUID.toLong), "test")

        // Even though the connection is stashed it will be disassociated
        inboundHandleProbe.expectMsgType[AssociationHandle.Disassociated]

      } finally shutdown(thisSystem)

    }

    // retry a few times as the temporaryServerAddress can be taken by the time the new actor system
    // binds
    def selectionAndBind(
        config: Config,
        thisSystem: ActorSystem,
        probe: TestProbe,
        retries: Int = 3): (ActorSystem, ActorSelection) = {
      val otherAddress = temporaryServerAddress()
      val otherConfig = ConfigFactory.parseString(s"""
              pekko.remote.classic.netty.tcp.port = ${otherAddress.getPort}
              """).withFallback(config)
      val otherSelection =
        thisSystem.actorSelection(s"pekko.tcp://other-system@localhost:${otherAddress.getPort}/user/echo")
      otherSelection.tell("ping", probe.ref)
      probe.expectNoMessage(200.millis)
      try {
        (ActorSystem("other-system", otherConfig), otherSelection)
      } catch {
        case NonFatal(ex) if ex.getMessage.contains("Failed to bind") && retries > 0 =>
          selectionAndBind(config, thisSystem, probe, retries = retries - 1)
        case other =>
          throw other
      }
    }

    "be able to connect to system even if it's not there at first" in {
      val config = ConfigFactory.parseString(s"""
            pekko.remote.classic.enabled-transports = ["pekko.remote.classic.netty.tcp"]
            pekko.remote.classic.netty.tcp.port = 0
            pekko.remote.classic.retry-gate-closed-for = 5s
            """).withFallback(remoteSystem.settings.config)
      val thisSystem = ActorSystem("this-system", config)
      try {
        muteSystem(thisSystem)
        val probe = new TestProbe(thisSystem)
        val (otherSystem, otherSelection) = selectionAndBind(config, thisSystem, probe)
        try {
          muteSystem(otherSystem)
          probe.expectNoMessage(2.seconds)
          otherSystem.actorOf(Props[Echo2](), "echo")
          within(5.seconds) {
            awaitAssert {
              otherSelection.tell("ping", probe.ref)
              assert(probe.expectMsgType[(String, ActorRef)](500.millis)._1 == "pong")
            }
          }
        } finally {
          shutdown(otherSystem)
        }
      } finally {
        shutdown(thisSystem)
      }
    }

    "allow other system to connect even if it's not there at first" in {
      val config = ConfigFactory.parseString(s"""
            pekko.remote.classic.enabled-transports = ["pekko.remote.classic.netty.tcp"]
            pekko.remote.classic.netty.tcp.port = 0
            pekko.remote.classic.retry-gate-closed-for = 5s
            """).withFallback(remoteSystem.settings.config)
      val thisSystem = ActorSystem("this-system", config)
      try {
        muteSystem(thisSystem)
        val thisProbe = new TestProbe(thisSystem)
        val thisSender = thisProbe.ref
        thisSystem.actorOf(Props[Echo2](), "echo")
        val otherAddress = temporaryServerAddress()
        val otherConfig = ConfigFactory.parseString(s"""
              pekko.remote.classic.netty.tcp.port = ${otherAddress.getPort}
              """).withFallback(config)
        val otherSelection =
          thisSystem.actorSelection(s"pekko.tcp://other-system@localhost:${otherAddress.getPort}/user/echo")
        otherSelection.tell("ping", thisSender)
        thisProbe.expectNoMessage(1.seconds)
        val otherSystem = ActorSystem("other-system", otherConfig)
        try {
          muteSystem(otherSystem)
          thisProbe.expectNoMessage(2.seconds)
          val otherProbe = new TestProbe(otherSystem)
          val otherSender = otherProbe.ref
          val thisSelection =
            otherSystem.actorSelection(s"pekko.tcp://this-system@localhost:${port(thisSystem, "tcp")}/user/echo")
          within(5.seconds) {
            awaitAssert {
              thisSelection.tell("ping", otherSender)
              assert(otherProbe.expectMsgType[(String, ActorRef)](500.millis)._1 == "pong")
            }
          }
        } finally {
          shutdown(otherSystem)
        }
      } finally {
        shutdown(thisSystem)
      }
    }
  }
}
