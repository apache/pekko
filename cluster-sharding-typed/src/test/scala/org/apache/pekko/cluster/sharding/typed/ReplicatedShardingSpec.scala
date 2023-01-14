/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed

import java.util.concurrent.ThreadLocalRandom
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.{ ActorTestKit, LogCapturing, ScalaTestWithActorTestKit }
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.cluster.MemberStatus
import pekko.cluster.sharding.typed.ReplicatedShardingSpec.DataCenter
import pekko.cluster.sharding.typed.ReplicatedShardingSpec.Normal
import pekko.cluster.sharding.typed.ReplicatedShardingSpec.ReplicationType
import pekko.cluster.sharding.typed.ReplicatedShardingSpec.Role
import pekko.cluster.sharding.typed.scaladsl.Entity
import pekko.cluster.typed.Cluster
import pekko.cluster.typed.Join
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.ReplicaId
import pekko.persistence.typed.scaladsl.ReplicatedEventSourcing
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.serialization.jackson.CborSerializable
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.cluster.sharding.typed.ReplicatedShardingSpec.MyReplicatedIntSet
import pekko.cluster.sharding.typed.ReplicatedShardingSpec.MyReplicatedStringSet
import pekko.persistence.typed.ReplicationId
import com.typesafe.config.Config
import pekko.util.ccompat._
import org.scalatest.time.Span

@ccompatUsedUntil213
object ReplicatedShardingSpec {
  def commonConfig = ConfigFactory.parseString("""
      pekko.loglevel = DEBUG
      pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
      pekko.actor.provider = "cluster"
      pekko.remote.classic.netty.tcp.port = 0
      pekko.remote.artery.canonical.port = 0""").withFallback(PersistenceTestKitPlugin.config)

  def roleAConfig = ConfigFactory.parseString("""
            pekko.cluster.roles = ["DC-A"]
            """.stripMargin).withFallback(commonConfig)

  def roleBConfig = ConfigFactory.parseString("""
            pekko.cluster.roles = ["DC-B"]
            """.stripMargin).withFallback(commonConfig)

  def dcAConfig = ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = "DC-A"
      """).withFallback(commonConfig)

  def dcBConfig = ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = "DC-B"
      """).withFallback(commonConfig)

  sealed trait ReplicationType
  case object Role extends ReplicationType
  case object DataCenter extends ReplicationType
  case object Normal extends ReplicationType

  val AllReplicas = Set(ReplicaId("DC-A"), ReplicaId("DC-B"))

  object MyReplicatedStringSet {
    sealed trait Command extends CborSerializable
    case class Add(text: String) extends Command
    case class GetTexts(replyTo: ActorRef[Texts]) extends Command

    case class Texts(texts: Set[String]) extends CborSerializable

    def apply(replicationId: ReplicationId): Behavior[Command] =
      ReplicatedEventSourcing.commonJournalConfig( // it isn't really shared as it is in memory
        replicationId,
        AllReplicas,
        PersistenceTestKitReadJournal.Identifier) { replicationContext =>
        EventSourcedBehavior[Command, String, Set[String]](
          replicationContext.persistenceId,
          Set.empty[String],
          (state, command) =>
            command match {
              case Add(text) =>
                Effect.persist(text)
              case GetTexts(replyTo) =>
                replyTo ! Texts(state)
                Effect.none
            },
          (state, event) => state + event)
          .withJournalPluginId(PersistenceTestKitPlugin.PluginId)
          .withEventPublishing(true)
      }

    def provider(replicationType: ReplicationType) =
      ReplicatedEntityProvider[MyReplicatedStringSet.Command](
        // all replicas
        "StringSet",
        AllReplicas) { (entityTypeKey, replicaId) =>
        // factory for replicated entity for a given replica
        val entity = {
          val e = Entity(entityTypeKey) { entityContext =>
            MyReplicatedStringSet(ReplicationId.fromString(entityContext.entityId))
          }
          replicationType match {
            case Role =>
              e.withRole(replicaId.id)
            case DataCenter =>
              e.withDataCenter(replicaId.id)
            case Normal =>
              e
          }
        }
        ReplicatedEntity(replicaId, entity)
      }.withDirectReplication(true)

  }

  object MyReplicatedIntSet {
    sealed trait Command extends CborSerializable
    case class Add(text: Int) extends Command
    case class GetInts(replyTo: ActorRef[Ints]) extends Command
    case class Ints(ints: Set[Int]) extends CborSerializable

    def apply(id: ReplicationId, allReplicas: Set[ReplicaId]): Behavior[Command] =
      ReplicatedEventSourcing.commonJournalConfig( // it isn't really shared as it is in memory
        id,
        allReplicas,
        PersistenceTestKitReadJournal.Identifier) { replicationContext =>
        EventSourcedBehavior[Command, Int, Set[Int]](
          replicationContext.persistenceId,
          Set.empty[Int],
          (state, command) =>
            command match {
              case Add(int) =>
                Effect.persist(int)
              case GetInts(replyTo) =>
                replyTo ! Ints(state)
                Effect.none
            },
          (state, event) => state + event)
          .withJournalPluginId(PersistenceTestKitPlugin.PluginId)
          .withEventPublishing(true)
      }

    def provider(replicationType: ReplicationType) =
      ReplicatedEntityProvider[MyReplicatedIntSet.Command]("IntSet", AllReplicas) { (entityTypeKey, replicaId) =>
        val entity = {
          val e = Entity(entityTypeKey) { entityContext =>
            val replicationId = ReplicationId.fromString(entityContext.entityId)
            MyReplicatedIntSet(replicationId, AllReplicas)
          }
          replicationType match {
            case Role =>
              e.withRole(replicaId.id)
            case DataCenter =>
              e.withDataCenter(replicaId.id)
            case Normal =>
              e
          }
        }
        ReplicatedEntity(replicaId, entity)
      }.withDirectReplication(true)
  }
}

object ProxyActor {
  sealed trait Command
  case class ForwardToRandomString(entityId: String, msg: MyReplicatedStringSet.Command) extends Command
  case class ForwardToAllString(entityId: String, msg: MyReplicatedStringSet.Command) extends Command
  case class ForwardToRandomInt(entityId: String, msg: MyReplicatedIntSet.Command) extends Command
  case class ForwardToAllInt(entityId: String, msg: MyReplicatedIntSet.Command) extends Command

  def apply(replicationType: ReplicationType): Behavior[Command] = Behaviors.setup { context =>
    val replicatedShardingStringSet: ReplicatedSharding[MyReplicatedStringSet.Command] =
      ReplicatedShardingExtension(context.system).init(MyReplicatedStringSet.provider(replicationType))
    val replicatedShardingIntSet: ReplicatedSharding[MyReplicatedIntSet.Command] =
      ReplicatedShardingExtension(context.system).init(MyReplicatedIntSet.provider(replicationType))

    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case ForwardToAllString(entityId, cmd) =>
          val entityRefs = replicatedShardingStringSet.entityRefsFor(entityId)
          ctx.log.infoN("Entity refs {}", entityRefs)
          entityRefs.foreach {
            case (replica, ref) =>
              ctx.log.infoN("Forwarding to replica {} ref {}", replica, ref)
              ref ! cmd
          }
          Behaviors.same
        case ForwardToRandomString(entityId, cmd) =>
          val refs = replicatedShardingStringSet.entityRefsFor(entityId)
          val chosenIdx = ThreadLocalRandom.current().nextInt(refs.size)
          val chosen = refs.values.toIndexedSeq(chosenIdx)
          ctx.log.info("Forwarding to {}", chosen)
          chosen ! cmd
          Behaviors.same
        case ForwardToAllInt(entityId, cmd) =>
          replicatedShardingIntSet.entityRefsFor(entityId).foreach {
            case (_, ref) => ref ! cmd
          }
          Behaviors.same
        case ForwardToRandomInt(entityId, cmd) =>
          val refs =
            replicatedShardingIntSet.entityRefsFor(entityId)
          val chosenIdx = ThreadLocalRandom.current().nextInt(refs.size)
          refs.values.toIndexedSeq(chosenIdx) ! cmd
          Behaviors.same
      }
    }
  }
}

class NormalReplicatedShardingSpec
    extends ReplicatedShardingSpec(Normal, ReplicatedShardingSpec.commonConfig, ReplicatedShardingSpec.commonConfig)
class RoleReplicatedShardingSpec
    extends ReplicatedShardingSpec(Role, ReplicatedShardingSpec.roleAConfig, ReplicatedShardingSpec.roleBConfig)
class DataCenterReplicatedShardingSpec
    extends ReplicatedShardingSpec(DataCenter, ReplicatedShardingSpec.dcAConfig, ReplicatedShardingSpec.dcBConfig)

abstract class ReplicatedShardingSpec(replicationType: ReplicationType, configA: Config, configB: Config)
    extends ScalaTestWithActorTestKit(configA)
    with AnyWordSpecLike
    with LogCapturing {

  // don't retry quite so quickly
  override implicit val patience: PatienceConfig =
    PatienceConfig(testKit.testKitSettings.DefaultTimeout.duration, Span(500, org.scalatest.time.Millis))

  val system2 = ActorSystem(Behaviors.ignore[Any], name = system.name, config = configB)

  override protected def afterAll(): Unit = {
    super.afterAll()
    ActorTestKit.shutdown(system2)
  }

  "Replicated sharding" should {

    "form a one node cluster" in {
      Cluster(system).manager ! Join(Cluster(system).selfMember.address)
      Cluster(system2).manager ! Join(Cluster(system).selfMember.address)

      eventually {
        Cluster(system).state.members.unsorted.size should ===(2)
        Cluster(system).state.members.unsorted.map(_.status) should ===(Set[MemberStatus](MemberStatus.Up))
      }
      eventually {
        Cluster(system2).state.members.unsorted.size should ===(2)
        Cluster(system2).state.members.unsorted.map(_.status) should ===(Set[MemberStatus](MemberStatus.Up))
      }
    }

    "start replicated sharding on both nodes" in {
      def start(sys: ActorSystem[_]) = {
        ReplicatedShardingExtension(sys).init(MyReplicatedStringSet.provider(replicationType))
        ReplicatedShardingExtension(sys).init(MyReplicatedIntSet.provider(replicationType))
      }
      start(system)
      start(system2)
    }

    "forward to replicas" in {
      val proxy: ActorRef[ProxyActor.Command] = spawn(ProxyActor(replicationType))

      proxy ! ProxyActor.ForwardToAllString("id1", MyReplicatedStringSet.Add("to-all"))
      proxy ! ProxyActor.ForwardToRandomString("id1", MyReplicatedStringSet.Add("to-random"))

      eventually {
        val probe = createTestProbe[MyReplicatedStringSet.Texts]()
        proxy ! ProxyActor.ForwardToAllString("id1", MyReplicatedStringSet.GetTexts(probe.ref))
        val responses: Seq[MyReplicatedStringSet.Texts] = probe.receiveMessages(2)
        val uniqueTexts = responses.flatMap(res => res.texts).toSet
        uniqueTexts should ===(Set("to-all", "to-random"))
      }

      proxy ! ProxyActor.ForwardToAllInt("id1", MyReplicatedIntSet.Add(10))
      proxy ! ProxyActor.ForwardToRandomInt("id1", MyReplicatedIntSet.Add(11))

      eventually {
        val probe = createTestProbe[MyReplicatedIntSet.Ints]()
        proxy ! ProxyActor.ForwardToAllInt("id1", MyReplicatedIntSet.GetInts(probe.ref))
        val responses: Seq[MyReplicatedIntSet.Ints] = probe.receiveMessages(2)
        val uniqueTexts = responses.flatMap(res => res.ints).toSet
        uniqueTexts should ===(Set(10, 11))
      }

      // This is also done in 'afterAll', but we do it here as well so we can see the
      // logging to diagnose
      // https://github.com/akka/akka/issues/30501 and
      // https://github.com/akka/akka/issues/30502
      ActorTestKit.shutdown(system2)
    }
  }

}
