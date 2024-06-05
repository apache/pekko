/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed
package internal

import java.net.URLEncoder
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.Future
import scala.runtime.AbstractPartialFunction

import org.apache.pekko
import pekko.actor.ActorRefProvider
import pekko.actor.ExtendedActorSystem
import pekko.actor.InternalActorRef
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.Props
import pekko.actor.typed.internal.InternalRecipientRef
import pekko.actor.typed.internal.PoisonPill
import pekko.actor.typed.internal.PoisonPillInterceptor
import pekko.actor.typed.internal.adapter.ActorRefAdapter
import pekko.actor.typed.internal.adapter.ActorSystemAdapter
import pekko.actor.typed.scaladsl.Behaviors
import pekko.annotation.{ InternalApi, InternalStableApi }
import pekko.cluster.ClusterSettings.DataCenter
import pekko.cluster.sharding.ShardCoordinator
import pekko.cluster.sharding.ShardCoordinator.ShardAllocationStrategy
import pekko.cluster.sharding.ShardRegion
import pekko.cluster.sharding.ShardRegion.{ StartEntity => ClassicStartEntity }
import pekko.cluster.sharding.typed.scaladsl.EntityContext
import pekko.cluster.typed.Cluster
import pekko.event.Logging
import pekko.event.LoggingAdapter
import pekko.japi.function.{ Function => JFunction }
import pekko.pattern.AskTimeoutException
import pekko.pattern.PromiseActorRef
import pekko.pattern.StatusReply
import pekko.util.{ unused, ByteString, Timeout }
import pekko.util.FutureConverters._
import pekko.util.JavaDurationConverters._

/**
 * INTERNAL API
 * Extracts entityId and unwraps ShardingEnvelope and StartEntity messages.
 * Other messages are delegated to the given `ShardingMessageExtractor`.
 */
@InternalApi private[pekko] class ExtractorAdapter[E, M](delegate: ShardingMessageExtractor[E, M])
    extends ShardingMessageExtractor[Any, M] {
  override def entityId(message: Any): String = {
    message match {
      case ShardingEnvelope(entityId, _) => entityId // also covers ClassicStartEntity in ShardingEnvelope
      case ClassicStartEntity(entityId)  => entityId
      case msg                           => delegate.entityId(msg.asInstanceOf[E])
    }
  }

  override def shardId(entityId: String): String = delegate.shardId(entityId)

  override def unwrapMessage(message: Any): M = {
    message match {
      case ShardingEnvelope(_, msg: M @unchecked) =>
        // also covers ClassicStartEntity in ShardingEnvelope
        msg
      case msg: ClassicStartEntity =>
        // not really of type M, but erased and StartEntity is only handled internally, not delivered to the entity
        msg.asInstanceOf[M]
      case msg =>
        delegate.unwrapMessage(msg.asInstanceOf[E])
    }
  }

  override def toString: String = delegate.toString
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class EntityTypeKeyImpl[T](name: String, messageClassName: String)
    extends javadsl.EntityTypeKey[T]
    with scaladsl.EntityTypeKey[T] {

  override def toString: String = s"EntityTypeKey[$messageClassName]($name)"

  private[pekko] def asJava: javadsl.EntityTypeKey[T] = this
}

/** INTERNAL API */
@InternalApi private[pekko] final class ClusterShardingImpl(system: ActorSystem[_])
    extends javadsl.ClusterSharding
    with scaladsl.ClusterSharding {

  import pekko.actor.typed.scaladsl.adapter._

  require(
    system.isInstanceOf[ActorSystemAdapter[_]],
    "only adapted classic actor systems can be used for cluster features")

  private val cluster = Cluster(system)
  private val classicSystem: ExtendedActorSystem = system.toClassic.asInstanceOf[ExtendedActorSystem]
  private val classicSharding = pekko.cluster.sharding.ClusterSharding(classicSystem)
  private val log: LoggingAdapter = Logging(classicSystem, classOf[scaladsl.ClusterSharding])

  // typeKey.name to messageClassName
  private val regions: ConcurrentHashMap[String, String] = new ConcurrentHashMap
  private val proxies: ConcurrentHashMap[String, String] = new ConcurrentHashMap
  private val shardCommandActors: ConcurrentHashMap[String, ActorRef[scaladsl.ClusterSharding.ShardCommand]] =
    new ConcurrentHashMap

  // scaladsl impl
  override def init[M, E](entity: scaladsl.Entity[M, E]): ActorRef[E] = {
    val settings = entity.settings match {
      case None    => ClusterShardingSettings(system)
      case Some(s) => s
    }

    val extractor = (entity.messageExtractor match {
      case None    => new HashCodeMessageExtractor[M](settings.numberOfShards)
      case Some(e) => e
    }).asInstanceOf[ShardingMessageExtractor[E, M]]

    val settingsWithRole = entity.role.fold(settings)(settings.withRole)
    val settingsWithDataCenter = entity.dataCenter.fold(settingsWithRole)(settingsWithRole.withDataCenter)

    internalInit(
      entity.createBehavior,
      entity.entityProps,
      entity.typeKey,
      entity.stopMessage,
      settingsWithDataCenter,
      extractor,
      entity.allocationStrategy)
  }

  // javadsl impl
  override def init[M, E](entity: javadsl.Entity[M, E]): ActorRef[E] = {
    import pekko.util.OptionConverters._
    init(
      new scaladsl.Entity(
        createBehavior = (ctx: EntityContext[M]) =>
          entity.createBehavior(new javadsl.EntityContext[M](entity.typeKey, ctx.entityId, ctx.shard)),
        typeKey = entity.typeKey.asScala,
        stopMessage = entity.stopMessage.toScala,
        entityProps = entity.entityProps,
        settings = entity.settings.toScala,
        messageExtractor = entity.messageExtractor.toScala,
        allocationStrategy = entity.allocationStrategy.toScala,
        role = entity.role.toScala,
        dataCenter = entity.dataCenter.toScala))
  }

  private def internalInit[M, E](
      behavior: EntityContext[M] => Behavior[M],
      entityProps: Props,
      typeKey: scaladsl.EntityTypeKey[M],
      stopMessage: Option[M],
      settings: ClusterShardingSettings,
      extractor: ShardingMessageExtractor[E, M],
      allocationStrategy: Option[ShardAllocationStrategy]): ActorRef[E] = {

    val extractorAdapter = new ExtractorAdapter(extractor)
    // !!!important is only applicable if you know that isDefinedAt(x) is always called before apply(x) (with the same x)
    val extractEntityId: ShardRegion.ExtractEntityId = new AbstractPartialFunction[Any, (String, Any)] {
      var cache: String = _

      override def isDefinedAt(msg: Any): Boolean = {
        cache = extractorAdapter.entityId(msg)
        cache != null
      }

      override def apply(x: Any): (String, Any) = (cache, extractorAdapter.unwrapMessage(x))
    }
    val extractShardId: ShardRegion.ExtractShardId = { message =>
      extractorAdapter.entityId(message) match {
        case null => null
        case eid  => extractorAdapter.shardId(eid)
      }
    }

    val ref =
      if (settings.shouldHostShard(cluster)) {
        log.info("Starting Shard Region [{}]...", typeKey.name)

        val shardCommandDelegator: ActorRef[scaladsl.ClusterSharding.ShardCommand] =
          shardCommandActors.computeIfAbsent(
            typeKey.name,
            new java.util.function.Function[String, ActorRef[scaladsl.ClusterSharding.ShardCommand]] {
              override def apply(t: String): ActorRef[scaladsl.ClusterSharding.ShardCommand] = {
                system.systemActorOf(
                  ShardCommandActor.behavior(stopMessage.getOrElse(PoisonPill)),
                  URLEncoder.encode(typeKey.name, ByteString.UTF_8) + "ShardCommandDelegator")
              }
            })

        def poisonPillInterceptor(behv: Behavior[M]): Behavior[M] = {
          stopMessage match {
            case Some(_) => behv
            case None    => Behaviors.intercept(() => new PoisonPillInterceptor[M])(behv)
          }
        }

        val classicEntityPropsFactory: String => pekko.actor.Props = { entityId =>
          val behv = behavior(new EntityContext(typeKey, entityId, shardCommandDelegator))
          PropsAdapter(poisonPillInterceptor(behv), entityProps)
        }
        classicSharding.internalStart(
          typeKey.name,
          classicEntityPropsFactory,
          ClusterShardingSettings.toClassicSettings(settings),
          extractEntityId,
          extractShardId,
          allocationStrategy.getOrElse(defaultShardAllocationStrategy(settings)),
          stopMessage.getOrElse(PoisonPill))
      } else {
        log.info(
          "Starting Shard Region Proxy [{}] (no actors will be hosted on this node) " +
          "for role [{}] and dataCenter [{}] ...",
          typeKey.name,
          settings.role,
          settings.dataCenter)

        classicSharding.startProxy(
          typeKey.name,
          settings.role,
          dataCenter = settings.dataCenter,
          extractEntityId,
          extractShardId)
      }

    val messageClassName = typeKey.asInstanceOf[EntityTypeKeyImpl[M]].messageClassName

    val typeNames = if (settings.shouldHostShard(cluster)) regions else proxies

    typeNames.putIfAbsent(typeKey.name, messageClassName) match {
      case spawnedMessageClassName: String if messageClassName != spawnedMessageClassName =>
        throw new IllegalArgumentException(s"[${typeKey.name}] already initialized for [$spawnedMessageClassName]")
      case _ => ()
    }

    ActorRefAdapter(ref)
  }

  override def entityRefFor[M](typeKey: scaladsl.EntityTypeKey[M], entityId: String): scaladsl.EntityRef[M] = {
    new EntityRefImpl[M](
      classicSharding.shardRegion(typeKey.name),
      entityId,
      typeKey.asInstanceOf[EntityTypeKeyImpl[M]])
  }

  override def entityRefFor[M](
      typeKey: scaladsl.EntityTypeKey[M],
      entityId: String,
      dataCenter: DataCenter): scaladsl.EntityRef[M] = {
    if (dataCenter == cluster.selfMember.dataCenter)
      entityRefFor(typeKey, entityId).asInstanceOf[EntityRefImpl[M]].withDataCenter(Some(dataCenter))
    else
      new EntityRefImpl[M](
        classicSharding.shardRegionProxy(typeKey.name, dataCenter),
        entityId,
        typeKey.asInstanceOf[EntityTypeKeyImpl[M]],
        Some(dataCenter))
  }

  override def entityRefFor[M](typeKey: javadsl.EntityTypeKey[M], entityId: String): javadsl.EntityRef[M] = {
    new EntityRefImpl[M](
      classicSharding.shardRegion(typeKey.name),
      entityId,
      typeKey.asInstanceOf[EntityTypeKeyImpl[M]])
  }

  override def entityRefFor[M](
      typeKey: javadsl.EntityTypeKey[M],
      entityId: String,
      dataCenter: String): javadsl.EntityRef[M] = {
    if (dataCenter == cluster.selfMember.dataCenter)
      entityRefFor(typeKey, entityId).asInstanceOf[EntityRefImpl[M]].withDataCenter(Some(dataCenter))
    else
      new EntityRefImpl[M](
        classicSharding.shardRegionProxy(typeKey.name, dataCenter),
        entityId,
        typeKey.asInstanceOf[EntityTypeKeyImpl[M]],
        Some(dataCenter))
  }

  override def defaultShardAllocationStrategy(settings: ClusterShardingSettings): ShardAllocationStrategy = {
    if (settings.tuningParameters.leastShardAllocationAbsoluteLimit > 0) {
      // new algorithm
      val absoluteLimit = settings.tuningParameters.leastShardAllocationAbsoluteLimit
      val relativeLimit = settings.tuningParameters.leastShardAllocationRelativeLimit
      ShardAllocationStrategy.leastShardAllocationStrategy(absoluteLimit, relativeLimit)
    } else {
      // old algorithm
      val threshold = settings.tuningParameters.leastShardAllocationRebalanceThreshold
      val maxSimultaneousRebalance = settings.tuningParameters.leastShardAllocationMaxSimultaneousRebalance
      new ShardCoordinator.LeastShardAllocationStrategy(threshold, maxSimultaneousRebalance)
    }
  }

  override lazy val shardState: ActorRef[ClusterShardingQuery] =
    system.systemActorOf(ShardingState.behavior(classicSharding), "typedShardState")

}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class EntityRefImpl[M](
    shardRegion: pekko.actor.ActorRef,
    override val entityId: String,
    override val typeKey: EntityTypeKeyImpl[M],
    override val dataCenter: Option[String] = None)
    extends javadsl.EntityRef[M]
    with scaladsl.EntityRef[M]
    with InternalRecipientRef[M] {

  override def hashCode(): Int =
    // 3 and 5 chosen as primes which are +/- 1 from a power-of-two
    ((entityId.hashCode * 3) + typeKey.hashCode) * 5 + dataCenter.hashCode

  override def equals(other: Any): Boolean =
    other match {
      case eri: EntityRefImpl[_] =>
        (eri.entityId == entityId) &&
        (eri.typeKey == typeKey) &&
        (eri.dataCenter == dataCenter)
      case _ => false
    }

  override val refPrefix = URLEncoder.encode(s"${typeKey.name}-$entityId", ByteString.UTF_8)

  override def tell(msg: M): Unit =
    shardRegion ! ShardingEnvelope(entityId, msg)

  override def ask[U](message: ActorRef[U] => M)(implicit timeout: Timeout): Future[U] = {
    val replyTo = new EntityPromiseRef[U](shardRegion.asInstanceOf[InternalActorRef], timeout, refPrefix)
    val m = message(replyTo.ref)
    if (replyTo.promiseRef ne null) replyTo.promiseRef.messageClassName = m.getClass.getName
    replyTo.ask(shardRegion, entityId, m, timeout)
  }

  override def ask[U](message: JFunction[ActorRef[U], M], timeout: Duration): CompletionStage[U] =
    ask[U](replyTo => message.apply(replyTo))(timeout.asScala).asJava

  override def askWithStatus[Res](f: ActorRef[StatusReply[Res]] => M)(implicit timeout: Timeout): Future[Res] =
    StatusReply.flattenStatusFuture(ask[StatusReply[Res]](f))

  override def askWithStatus[Res](f: ActorRef[StatusReply[Res]] => M, timeout: Duration): CompletionStage[Res] =
    askWithStatus(f.apply)(timeout.asScala).asJava

  /** Similar to [[pekko.actor.typed.scaladsl.AskPattern.PromiseRef]] but for an `EntityRef` target. */
  @InternalApi
  private final class EntityPromiseRef[U](classic: InternalActorRef, timeout: Timeout, refPathPrefix: String) {
    import pekko.actor.typed.internal.{ adapter => adapt }

    // Note: _promiseRef mustn't have a type pattern, since it can be null
    private[this] val (_ref: ActorRef[U], _future: Future[U], _promiseRef) =
      if (classic.isTerminated)
        (
          adapt.ActorRefAdapter[U](classic.provider.deadLetters),
          Future.failed[U](
            new AskTimeoutException(s"Recipient shard region of [${EntityRefImpl.this}] had already been terminated.")),
          null)
      else if (timeout.duration.length <= 0)
        (
          adapt.ActorRefAdapter[U](classic.provider.deadLetters),
          Future.failed[U](
            new IllegalArgumentException(
              s"Timeout length must be positive, question not sent to [${EntityRefImpl.this}]")),
          null)
      else {
        // note that the real messageClassName will be set afterwards, replyTo pattern
        val a =
          PromiseActorRef(
            classic.provider,
            timeout,
            targetName = EntityRefImpl.this,
            messageClassName = "unknown",
            refPathPrefix)
        val b = adapt.ActorRefAdapter[U](a)
        (b, a.result.future.asInstanceOf[Future[U]], a)
      }

    val ref: ActorRef[U] = _ref
    val future: Future[U] = _future
    val promiseRef: PromiseActorRef = _promiseRef

    @InternalStableApi
    private[pekko] def ask[T](
        shardRegion: pekko.actor.ActorRef,
        entityId: String,
        message: T,
        @unused timeout: Timeout): Future[U] = {
      shardRegion ! ShardingEnvelope(entityId, message)
      future
    }
  }

  // impl InternalRecipientRef
  override def provider: ActorRefProvider = {
    import pekko.actor.typed.scaladsl.adapter._
    shardRegion.toTyped.asInstanceOf[InternalRecipientRef[_]].provider
  }

  // impl InternalRecipientRef
  def isTerminated: Boolean = {
    import pekko.actor.typed.scaladsl.adapter._
    shardRegion.toTyped.asInstanceOf[InternalRecipientRef[_]].isTerminated
  }

  override def toString: String = s"EntityRef($typeKey, $entityId)"

  /**
   * INTERNAL API
   */
  override private[pekko] def asJava: javadsl.EntityRef[M] = this

  private[internal] def withDataCenter(dataCenter: Option[String]): EntityRefImpl[M] =
    new EntityRefImpl[M](shardRegion, entityId, typeKey, dataCenter)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ShardCommandActor {
  import pekko.actor
  import pekko.actor.typed.scaladsl.adapter._
  import pekko.cluster.sharding.ShardRegion.{ Passivate => ClassicPassivate }

  def behavior(stopMessage: Any): Behavior[scaladsl.ClusterSharding.ShardCommand] = {
    def sendClassicPassivate(entity: ActorRef[_], classicSystem: actor.ActorSystem): Unit = {
      val classicRef = entity.toClassic
      val pathToShard = classicRef.path.elements.take(4).mkString("/")
      classicSystem.actorSelection(pathToShard).tell(ClassicPassivate(stopMessage), classicRef)
    }

    Behaviors.receive { (ctx, msg) =>
      val classicSystem = ctx.asScala.system.toClassic

      msg match {
        case scaladsl.ClusterSharding.Passivate(entity) =>
          sendClassicPassivate(entity, classicSystem)
          Behaviors.same
        case javadsl.ClusterSharding.Passivate(entity) =>
          sendClassicPassivate(entity, classicSystem)
          Behaviors.same
        case _ =>
          Behaviors.unhandled
      }
    }
  }

}
