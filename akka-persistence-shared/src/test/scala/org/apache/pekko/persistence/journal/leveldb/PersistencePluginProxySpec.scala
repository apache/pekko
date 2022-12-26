/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.journal.leveldb

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor._
import pekko.persistence._
import pekko.persistence.journal.PersistencePluginProxy
import pekko.testkit.{ PekkoSpec, TestProbe }

object PersistencePluginProxySpec {
  lazy val config =
    ConfigFactory.parseString(s"""
      pekko {
        actor {
          provider = remote
        }
        persistence {
          journal {
            plugin = "pekko.persistence.journal.proxy"
            proxy.target-journal-plugin = "pekko.persistence.journal.inmem"
          }
          snapshot-store {
            plugin = "pekko.persistence.snapshot-store.proxy"
            proxy.target-snapshot-store-plugin = "pekko.persistence.snapshot-store.local"
            local.dir = target/snapshots-PersistencePluginProxySpec
          }
        }
        remote {
          enabled-transports = ["pekko.remote.classic.netty.tcp"]
          classic.netty.tcp {
            hostname = "127.0.0.1"
            port = 0
          }
          artery.canonical {
            hostname = "127.0.0.1"
            port = 0
          }
        }
        loglevel = ERROR
        log-dead-letters = 0
        log-dead-letters-during-shutdown = off
        test.single-expect-default = 10s
      }
    """).withFallback(SharedLeveldbJournal.configToEnableJavaSerializationForTest)

  lazy val startTargetConfig =
    ConfigFactory.parseString("""
      |pekko.extensions = ["org.apache.pekko.persistence.journal.PersistencePluginProxyExtension"]
      |pekko.persistence {
      |  journal.proxy.start-target-journal = on
      |  snapshot-store.proxy.start-target-snapshot-store = on
      |}
    """.stripMargin)

  def targetAddressConfig(system: ActorSystem) =
    ConfigFactory.parseString(s"""
      |pekko.extensions = ["org.apache.pekko.persistence.Persistence"]
      |pekko.persistence.journal.auto-start-journals = [""]
      |pekko.persistence.journal.proxy.target-journal-address = "${system
                                  .asInstanceOf[ExtendedActorSystem]
                                  .provider
                                  .getDefaultAddress}"
      |pekko.persistence.snapshot-store.proxy.target-snapshot-store-address = "${system
                                  .asInstanceOf[ExtendedActorSystem]
                                  .provider
                                  .getDefaultAddress}"
    """.stripMargin)

  class ExamplePersistentActor(probe: ActorRef, name: String) extends NamedPersistentActor(name) {
    override def receiveRecover = {
      case RecoveryCompleted => // ignore
      case payload =>
        probe ! payload
    }
    override def receiveCommand = {
      case payload =>
        persist(payload) { _ =>
          probe ! payload
        }
    }
  }

  class ExampleApp(probe: ActorRef) extends Actor {
    val p = context.actorOf(Props(classOf[ExamplePersistentActor], probe, context.system.name))

    def receive = {
      case m => p.forward(m)
    }

  }
}

class PersistencePluginProxySpec
    extends PekkoSpec(PersistencePluginProxySpec.startTargetConfig.withFallback(PersistencePluginProxySpec.config))
    with Cleanup {
  import PersistencePluginProxySpec._

  val systemA = ActorSystem("SysA", config)
  val systemB = ActorSystem("SysB", targetAddressConfig(system).withFallback(PersistencePluginProxySpec.config))

  override protected def afterTermination(): Unit = {
    shutdown(systemA)
    shutdown(systemB)
    super.afterTermination()
  }

  "A persistence proxy".can {
    "be shared by multiple actor systems" in {

      val address = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress

      val probeA = new TestProbe(systemA)
      val probeB = new TestProbe(systemB)

      PersistencePluginProxy.setTargetLocation(systemA, address)

      val appA = systemA.actorOf(Props(classOf[ExampleApp], probeA.ref))
      val appB = systemB.actorOf(Props(classOf[ExampleApp], probeB.ref))

      appA ! "a1"
      appB ! "b1"

      probeA.expectMsg("a1")
      probeB.expectMsg("b1")

      val recoveredAppA = systemA.actorOf(Props(classOf[ExampleApp], probeA.ref))
      val recoveredAppB = systemB.actorOf(Props(classOf[ExampleApp], probeB.ref))

      recoveredAppA ! "a2"
      recoveredAppB ! "b2"

      probeA.expectMsg("a1")
      probeA.expectMsg("a2")

      probeB.expectMsg("b1")
      probeB.expectMsg("b2")
    }
  }
}
