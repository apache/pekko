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

package org.apache.pekko.remote.transport

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor._
import pekko.actor.DeadLetterSuppression
import pekko.dispatch.{ RequiresMessageQueue, UnboundedMessageQueueSemantics }
import pekko.pattern.{ ask, gracefulStop, pipe }
import pekko.remote.RARP
import pekko.remote.Remoting.RegisterTransportActor
import pekko.remote.transport.AssociationHandle.DisassociateInfo
import pekko.remote.transport.Transport._
import pekko.util.Timeout

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
trait TransportAdapterProvider {

  /**
   * Create the transport adapter that wraps an underlying transport.
   */
  def create(wrappedTransport: Transport, system: ExtendedActorSystem): Transport
}

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
class TransportAdapters(system: ExtendedActorSystem) extends Extension {
  val settings = RARP(system).provider.remoteSettings

  private val adaptersTable: Map[String, TransportAdapterProvider] = for ((name, fqn) <- settings.Adapters) yield {
    name -> system.dynamicAccess
      .createInstanceFor[TransportAdapterProvider](fqn, immutable.Seq.empty)
      .recover {
        case e => throw new IllegalArgumentException(s"Cannot instantiate transport adapter [$fqn]", e)
      }
      .get
  }

  def getAdapterProvider(name: String): TransportAdapterProvider = adaptersTable.get(name) match {
    case Some(provider) => provider
    case None           =>
      throw new IllegalArgumentException(s"There is no registered transport adapter provider with name: [$name]")
  }
}

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
object TransportAdaptersExtension extends ExtensionId[TransportAdapters] with ExtensionIdProvider {
  override def get(system: ActorSystem): TransportAdapters = super.get(system)
  override def get(system: ClassicActorSystemProvider): TransportAdapters = super.get(system)
  override def lookup = TransportAdaptersExtension
  override def createExtension(system: ExtendedActorSystem): TransportAdapters =
    new TransportAdapters(system)
}

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
trait SchemeAugmenter {
  protected def addedSchemeIdentifier: String

  protected def augmentScheme(originalScheme: String): String = s"$addedSchemeIdentifier.$originalScheme"

  protected def augmentScheme(address: Address): Address = address.copy(protocol = augmentScheme(address.protocol))

  protected def removeScheme(scheme: String): String =
    if (scheme.startsWith(s"$addedSchemeIdentifier."))
      scheme.drop(addedSchemeIdentifier.length + 1)
    else scheme

  protected def removeScheme(address: Address): Address = address.copy(protocol = removeScheme(address.protocol))
}

/**
 * An adapter that wraps a transport and provides interception
 */
@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
abstract class AbstractTransportAdapter(protected val wrappedTransport: Transport)(implicit val ec: ExecutionContext)
    extends Transport
    with SchemeAugmenter {

  protected def maximumOverhead: Int

  protected def interceptListen(
      listenAddress: Address,
      listenerFuture: Future[AssociationEventListener]): Future[AssociationEventListener]

  protected def interceptAssociate(remoteAddress: Address, statusPromise: Promise[AssociationHandle]): Unit

  override def schemeIdentifier: String = augmentScheme(wrappedTransport.schemeIdentifier)

  override def isResponsibleFor(address: Address): Boolean = wrappedTransport.isResponsibleFor(address)

  override def maximumPayloadBytes: Int = wrappedTransport.maximumPayloadBytes - maximumOverhead

  override def listen: Future[(Address, Promise[AssociationEventListener])] = {
    val upstreamListenerPromise: Promise[AssociationEventListener] = Promise()

    for {
      (listenAddress, listenerPromise) <- wrappedTransport.listen
      // Enforce ordering between the signalling of "listen ready" to upstream
      // and initialization happening in interceptListen
      _ <- listenerPromise.completeWith(interceptListen(listenAddress, upstreamListenerPromise.future)).future
    } yield (augmentScheme(listenAddress), upstreamListenerPromise)
  }

  /**
   * INTERNAL API
   * @return
   *  The address this Transport is listening to.
   */
  private[pekko] def boundAddress: Address = wrappedTransport match {
    // Need to do like this in the backport of #15007 to 2.3.x for binary compatibility reasons
    case t: AbstractTransportAdapter => t.boundAddress
    case t: netty.NettyTransport     => t.boundAddress
    case t: TestTransport            => t.boundAddress
    case _                           => null
  }

  override def associate(remoteAddress: Address): Future[AssociationHandle] = {
    // Prepare a future, and pass its promise to the manager
    val statusPromise: Promise[AssociationHandle] = Promise()

    interceptAssociate(removeScheme(remoteAddress), statusPromise)

    statusPromise.future
  }

  override def shutdown(): Future[Boolean] = wrappedTransport.shutdown()

}

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
abstract class AbstractTransportAdapterHandle(
    val originalLocalAddress: Address,
    val originalRemoteAddress: Address,
    val wrappedHandle: AssociationHandle,
    val addedSchemeIdentifier: String)
    extends AssociationHandle
    with SchemeAugmenter {

  def this(wrappedHandle: AssociationHandle, addedSchemeIdentifier: String) =
    this(wrappedHandle.localAddress, wrappedHandle.remoteAddress, wrappedHandle, addedSchemeIdentifier)

  override val localAddress = augmentScheme(originalLocalAddress)
  override val remoteAddress = augmentScheme(originalRemoteAddress)

}

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
object ActorTransportAdapter {
  sealed trait TransportOperation extends NoSerializationVerificationNeeded

  final case class ListenerRegistered(listener: AssociationEventListener) extends TransportOperation
  final case class AssociateUnderlying(remoteAddress: Address, statusPromise: Promise[AssociationHandle])
      extends TransportOperation
  final case class ListenUnderlying(listenAddress: Address, upstreamListener: Future[AssociationEventListener])
      extends TransportOperation
  final case class DisassociateUnderlying(info: DisassociateInfo = AssociationHandle.Unknown)
      extends TransportOperation
      with DeadLetterSuppression

  implicit val AskTimeout: Timeout = Timeout(5.seconds)
}

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
abstract class ActorTransportAdapter(wrappedTransport: Transport, system: ActorSystem)
    extends AbstractTransportAdapter(wrappedTransport)(system.dispatchers.internalDispatcher) {

  import ActorTransportAdapter._

  protected def managerName: String
  protected def managerProps: Props
  // Write once variable initialized when Listen is called.
  @volatile protected var manager: ActorRef = _

  private def registerManager(): Future[ActorRef] =
    (system.actorSelection("/system/transports") ? RegisterTransportActor(managerProps, managerName)).mapTo[ActorRef]

  override def interceptListen(
      listenAddress: Address,
      listenerPromise: Future[AssociationEventListener]): Future[AssociationEventListener] = {
    registerManager().map { mgr =>
      // Side effecting: storing the manager instance in volatile var
      // This is done only once: during the initialization of the protocol stack. The variable manager is not read
      // before listen is called.
      manager = mgr
      manager ! ListenUnderlying(listenAddress, listenerPromise)
      ActorAssociationEventListener(manager)
    }
  }

  override def interceptAssociate(remoteAddress: Address, statusPromise: Promise[AssociationHandle]): Unit =
    manager ! AssociateUnderlying(remoteAddress, statusPromise)

  override def shutdown(): Future[Boolean] =
    for {
      stopResult <- gracefulStop(manager, RARP(system).provider.remoteSettings.FlushWait)
      wrappedStopResult <- wrappedTransport.shutdown()
    } yield stopResult && wrappedStopResult
}

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
abstract class ActorTransportAdapterManager extends Actor with RequiresMessageQueue[UnboundedMessageQueueSemantics] {
  import ActorTransportAdapter.{ ListenUnderlying, ListenerRegistered }

  private var delayedEvents = immutable.Queue.empty[Any]

  protected var associationListener: AssociationEventListener = _
  protected var localAddress: Address = _
  private var uniqueId = 0L

  protected def nextId(): Long = {
    uniqueId += 1
    uniqueId
  }

  import context.dispatcher

  def receive: Receive = {
    case ListenUnderlying(listenAddress, upstreamListenerFuture) =>
      localAddress = listenAddress
      upstreamListenerFuture.future.map { ListenerRegistered(_) }.pipeTo(self)

    case ListenerRegistered(listener) =>
      associationListener = listener
      delayedEvents.foreach { self.tell(_, Actor.noSender) }
      delayedEvents = immutable.Queue.empty[Any]
      context.become(ready)

    /* Simple imitation of Stash. It is more lightweight as it does not need any specific dispatchers or additional
     * queue. The difference is that these messages will not survive a restart -- which is not needed here.
     * These messages will be processed in the ready state.
     */
    case otherEvent => delayedEvents = delayedEvents.enqueue(otherEvent)

  }

  protected def ready: Receive
}
