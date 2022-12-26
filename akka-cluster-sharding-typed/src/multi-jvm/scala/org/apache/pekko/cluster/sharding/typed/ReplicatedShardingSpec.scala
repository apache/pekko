/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed

import org.apache.pekko
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.cluster.MultiNodeClusterSpec
import pekko.cluster.sharding.typed.ReplicatedShardingSpec.TestRES.GetState
import pekko.cluster.sharding.typed.ReplicatedShardingSpec.TestRES.State
import pekko.cluster.sharding.typed.ReplicatedShardingSpec.TestRES.StoreMe
import pekko.cluster.sharding.typed.scaladsl.Entity
import pekko.cluster.typed.MultiNodeTypedClusterSpec
import pekko.persistence.journal.PersistencePluginProxy
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.ReplicaId
import pekko.persistence.typed.ReplicationId
import pekko.persistence.typed.scaladsl.ReplicatedEventSourcing
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.serialization.jackson.CborSerializable
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span

object ReplicatedShardingSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  commonConfig(ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
    pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    // for the proxy plugin
    pekko.actor.allow-java-serialization = on 
    pekko.actor.warn-about-java-serializer-usage = off
      """).withFallback(MultiNodeClusterSpec.clusterConfig))

  nodeConfig(first)(ConfigFactory.parseString("""
    pekko.persistence.journal.plugin = "pekko.persistence.journal.proxy"
    pekko.persistence.journal.proxy {
      start-target-journal = on
      target-journal-plugin = "pekko.persistence.journal.inmem"
    }
      """))

  nodeConfig(second)(ConfigFactory.parseString("""
    pekko.persistence.journal.plugin = "pekko.persistence.journal.proxy"
    pekko.persistence.journal.proxy {
      start-target-journal = off
      target-journal-plugin = "pekko.persistence.journal.inmem"
    }
      """))

  val AllReplicas = Set(ReplicaId("R1"), ReplicaId("R2"))

  object TestRES {
    sealed trait Command extends CborSerializable
    case class GetState(replyTo: ActorRef[State]) extends Command
    case class StoreMe(description: String, replyTo: ActorRef[Done]) extends Command

    case class State(all: List[String]) extends CborSerializable

    def apply(id: ReplicationId, ctx: ActorContext[Command]): EventSourcedBehavior[Command, String, State] = {
      // Relies on direct replication as there is no proxy query journal
      ReplicatedEventSourcing.commonJournalConfig(id, AllReplicas, PersistenceTestKitReadJournal.Identifier) {
        replicationContext =>
          ctx.log.info("Creating replica {}", replicationContext.replicationId)
          EventSourcedBehavior[Command, String, State](
            replicationContext.persistenceId,
            State(Nil),
            (state, command) =>
              command match {
                case GetState(replyTo) =>
                  replyTo ! state
                  Effect.none
                case StoreMe(evt, ack) =>
                  ctx.log.infoN("StoreMe {} {}", evt, replicationContext.replicationId)
                  Effect.persist(evt).thenRun(_ => ack ! Done)
              },
            (state, event) => {
              ctx.log.infoN(
                "EventHandler [{}] origin [{}] at [{}]",
                event,
                replicationContext.origin,
                replicationContext.replicationId)
              state.copy(all = event :: state.all)
            }).withEventPublishing(true)
      }
    }

    def provider(): ReplicatedEntityProvider[Command] = {
      ReplicatedEntityProvider[Command]("TestRES", AllReplicas) { (entityTypeKey, replicaId) =>
        ReplicatedEntity(replicaId,
          Entity(entityTypeKey) { entityContext =>
            Behaviors.setup { ctx =>
              TestRES(ReplicationId.fromString(entityContext.entityId), ctx)
            }
          })
      }.withDirectReplication(true) // this is required as we don't have a shared read journal
    }
  }
}

class ReplicatedShardingSpecMultiJvmNode1 extends ReplicatedShardingSpec
class ReplicatedShardingSpecMultiJvmNode2 extends ReplicatedShardingSpec

abstract class ReplicatedShardingSpec
    extends MultiNodeSpec(ReplicatedShardingSpec)
    with MultiNodeTypedClusterSpec
    with ScalaFutures
    with Eventually {
  import ReplicatedShardingSpec._

  implicit val patience: PatienceConfig = {
    import pekko.testkit.TestDuration
    PatienceConfig(testKitSettings.DefaultTimeout.duration.dilated * 2, Span(500, org.scalatest.time.Millis))
  }

  "Replicated sharding" should {
    "form cluster" in {
      formCluster(first, second)
      enterBarrier("cluster-fored")
    }

    "setup proxy plugin" in {
      PersistencePluginProxy.setTargetLocation(system, address(first))
      enterBarrier("proxy-setup")
    }

    "start replicated entities" in {
      val replicatedSharding: ReplicatedSharding[TestRES.Command] =
        ReplicatedShardingExtension(typedSystem).init(TestRES.provider())

      runOn(first) {
        val entityRefs = replicatedSharding.entityRefsFor("id1")
        val probe = TestProbe[Done]()
        entityRefs.size shouldEqual 2
        entityRefs.foreach {
          case (replica, ref) => ref ! StoreMe(s"from first to ${replica.id}", probe.ref)
        }
        probe.expectMessage(Done)
        probe.expectMessage(Done)

        eventually {
          entityRefs.foreach {
            case (_, ref) =>
              val probe = TestProbe[State]()
              ref ! GetState(probe.ref)
              probe.expectMessageType[State].all.toSet shouldEqual Set(s"from first to R1", s"from first to R2")
          }
        }
      }

      runOn(second) {
        eventually {
          val probe = TestProbe[State]()
          replicatedSharding.entityRefsFor("id1").head._2 ! GetState(probe.ref)
          probe.expectMessageType[State].all.toSet shouldEqual Set("from first to R1", "from first to R2")
        }
      }
      enterBarrier("done")

      runOn(second) {
        val entityRefs = replicatedSharding.entityRefsFor("id2")
        val probe = TestProbe[Done]()
        entityRefs.foreach {
          case (replica, ref) => ref ! StoreMe(s"from first to ${replica.id}", probe.ref)
        }
        probe.expectMessage(Done)
        probe.expectMessage(Done)
      }

      runOn(first) {
        eventually {
          val probe = TestProbe[State]()
          replicatedSharding.entityRefsFor("id2").head._2 ! GetState(probe.ref)
          probe.expectMessageType[State].all.toSet shouldEqual Set("from first to R1", "from first to R2")
        }
      }

      enterBarrier("done-2")
    }
  }
}
