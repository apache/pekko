/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal.adapter

import java.util.concurrent.CompletionStage

import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContextExecutor

import org.slf4j.{ Logger, LoggerFactory }

import org.apache.pekko
import pekko.{ actor => classic }
import pekko.Done
import pekko.actor
import pekko.actor.{ ActorRefProvider, Address, ExtendedActorSystem, InvalidMessageException }
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.DispatcherSelector
import pekko.actor.typed.Dispatchers
import pekko.actor.typed.Props
import pekko.actor.typed.Scheduler
import pekko.actor.typed.Settings
import pekko.actor.typed.SupervisorStrategy
import pekko.actor.typed.internal.ActorRefImpl
import pekko.actor.typed.internal.ExtensionsImpl
import pekko.actor.typed.internal.InternalRecipientRef
import pekko.actor.typed.internal.PropsImpl.DispatcherDefault
import pekko.actor.typed.internal.PropsImpl.DispatcherFromConfig
import pekko.actor.typed.internal.PropsImpl.DispatcherSameAsParent
import pekko.actor.typed.internal.SystemMessage
import pekko.actor.typed.scaladsl.Behaviors
import pekko.annotation.InternalApi

/**
 * INTERNAL API. Lightweight wrapper for presenting a classic ActorSystem to a Behavior (via the context).
 * Therefore it does not have a lot of vals, only the whenTerminated Future is cached after
 * its transformation because redoing that every time will add extra objects that persist for
 * a longer time; in all other cases the wrapper will just be spawned for a single call in
 * most circumstances.
 */
@InternalApi private[pekko] class ActorSystemAdapter[-T](val system: classic.ActorSystemImpl)
    extends ActorSystem[T]
    with ActorRef[T]
    with ActorRefImpl[T]
    with InternalRecipientRef[T]
    with ExtensionsImpl {

  // note that the (classic) system may not be initialized yet here, and that is fine because
  // it is unlikely that anything gets a hold of the extension until the system is started

  import ActorRefAdapter.sendSystemMessage

  override def classicSystem: classic.ActorSystem = system

  // Members declared in org.apache.pekko.actor.typed.ActorRef
  override def tell(msg: T): Unit = {
    if (msg == null) throw InvalidMessageException("[null] is not an allowed message")
    system.guardian ! msg
  }

  // impl ActorRefImpl
  override def isLocal: Boolean = true
  // impl ActorRefImpl
  override def sendSystem(signal: SystemMessage): Unit = sendSystemMessage(system.guardian, signal)

  // impl InternalRecipientRef
  override def provider: ActorRefProvider = system.provider
  // impl InternalRecipientRef
  def isTerminated: Boolean = whenTerminated.isCompleted

  final override val path: classic.ActorPath =
    classic.RootActorPath(classic.Address("akka", system.name)) / "user"

  override def toString: String = system.toString

  // Members declared in org.apache.pekko.actor.typed.ActorSystem
  override def deadLetters[U]: ActorRef[U] = ActorRefAdapter(system.deadLetters)

  private val cachedIgnoreRef: ActorRef[Nothing] = ActorRefAdapter(provider.ignoreRef)
  override def ignoreRef[U]: ActorRef[U] = cachedIgnoreRef.unsafeUpcast[U]

  override def dispatchers: Dispatchers = new Dispatchers {
    override def lookup(selector: DispatcherSelector): ExecutionContextExecutor =
      selector match {
        case DispatcherDefault(_)         => system.dispatcher
        case DispatcherFromConfig(str, _) => system.dispatchers.lookup(str)
        case DispatcherSameAsParent(_)    => system.dispatcher
        case unknown                      => throw new RuntimeException(s"Unsupported dispatcher selector: $unknown")
      }
    override def shutdown(): Unit = () // there was no shutdown in classic Akka
  }
  override def dynamicAccess: classic.DynamicAccess = system.dynamicAccess
  implicit override def executionContext: scala.concurrent.ExecutionContextExecutor = classicSystem.dispatcher
  override val log: Logger = LoggerFactory.getLogger(classOf[ActorSystem[_]])
  override def logConfiguration(): Unit = classicSystem.logConfiguration()
  override def name: String = classicSystem.name
  override val scheduler: Scheduler = new SchedulerAdapter(classicSystem.scheduler)
  override def settings: Settings = new Settings(classicSystem.settings)
  override def startTime: Long = classicSystem.startTime
  override def threadFactory: java.util.concurrent.ThreadFactory = system.threadFactory
  override def uptime: Long = classicSystem.uptime
  override def printTree: String = system.printTree

  import org.apache.pekko.dispatch.ExecutionContexts.parasitic

  override def terminate(): Unit = system.terminate()
  override lazy val whenTerminated: scala.concurrent.Future[pekko.Done] =
    system.whenTerminated.map(_ => Done)(parasitic)
  override lazy val getWhenTerminated: CompletionStage[pekko.Done] =
    FutureConverters.toJava(whenTerminated)

  override def systemActorOf[U](behavior: Behavior[U], name: String, props: Props): ActorRef[U] = {
    val ref = system.systemActorOf(
      PropsAdapter(
        () => Behaviors.supervise(behavior).onFailure(SupervisorStrategy.stop),
        props,
        rethrowTypedFailure = false),
      name)
    ActorRefAdapter(ref)
  }

  override def refPrefix: String = "user"

  override def address: Address = system.provider.getDefaultAddress

}

private[pekko] object ActorSystemAdapter {
  def apply(system: classic.ActorSystem): ActorSystem[Nothing] = AdapterExtension(system).adapter

  // to make sure we do never create more than one adapter for the same actor system
  class AdapterExtension(system: classic.ExtendedActorSystem) extends classic.Extension {
    val adapter = new ActorSystemAdapter(system.asInstanceOf[classic.ActorSystemImpl])
  }

  object AdapterExtension extends classic.ExtensionId[AdapterExtension] with classic.ExtensionIdProvider {
    override def get(system: classic.ActorSystem): AdapterExtension = super.get(system)
    override def lookup = AdapterExtension
    override def createExtension(system: classic.ExtendedActorSystem): AdapterExtension =
      new AdapterExtension(system)
  }

  /**
   * A classic extension to load configured typed extensions. It is loaded via
   * pekko.library-extensions. `loadExtensions` cannot be called from the AdapterExtension
   * directly because the adapter is created too early during typed actor system creation.
   *
   * When on the classpath typed extensions will be loaded for classic ActorSystems as well.
   */
  class LoadTypedExtensions(system: classic.ExtendedActorSystem) extends classic.Extension {
    ActorSystemAdapter.AdapterExtension(system).adapter.loadExtensions()
  }

  object LoadTypedExtensions extends classic.ExtensionId[LoadTypedExtensions] with classic.ExtensionIdProvider {
    override def lookup: actor.ExtensionId[_ <: actor.Extension] = this
    override def createExtension(system: ExtendedActorSystem): LoadTypedExtensions =
      new LoadTypedExtensions(system)
  }

  def toClassic[U](sys: ActorSystem[_]): classic.ActorSystem = sys.classicSystem
}
