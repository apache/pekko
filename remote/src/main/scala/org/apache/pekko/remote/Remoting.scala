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

package org.apache.pekko.remote

import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

import scala.collection.immutable
import scala.collection.immutable.{ HashMap, Seq }
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal

import scala.annotation.nowarn
import com.typesafe.config.Config

import org.apache.pekko
import pekko.Done
import pekko.actor._
import pekko.actor.ActorInitializationException
import pekko.actor.SupervisorStrategy._
import pekko.annotation.InternalStableApi
import pekko.dispatch.{ RequiresMessageQueue, UnboundedMessageQueueSemantics }
import pekko.dispatch.MessageDispatcher
import pekko.event.{ Logging, LoggingAdapter }
import pekko.pattern.{ ask, gracefulStop, pipe }
import pekko.remote.EndpointManager._
import pekko.remote.Remoting.TransportSupervisor
import pekko.remote.transport._
import pekko.remote.transport.PekkoPduCodec.Message
import pekko.remote.transport.Transport.{ ActorAssociationEventListener, AssociationEventListener, InboundAssociation }
import pekko.util.ByteString.UTF_8
import pekko.util.OptionVal
import pekko.util.ccompat._

/**
 * INTERNAL API
 */
private[remote] object AddressUrlEncoder {
  def apply(address: Address): String = URLEncoder.encode(address.toString, UTF_8)
}

/**
 * INTERNAL API
 */
private[pekko] final case class RARP(provider: RemoteActorRefProvider) extends Extension {
  def configureDispatcher(props: Props): Props = provider.remoteSettings.configureDispatcher(props)
}

/**
 * INTERNAL API
 */
private[pekko] object RARP extends ExtensionId[RARP] with ExtensionIdProvider {

  override def lookup = RARP

  override def createExtension(system: ExtendedActorSystem) = RARP(system.provider.asInstanceOf[RemoteActorRefProvider])
}

/**
 * INTERNAL API
 * Messages marked with this trait will be sent before other messages when buffering is active.
 * This means that these messages don't obey normal message ordering.
 * It is used for failure detector heartbeat messages.
 *
 * In Artery this is not used, and instead a preconfigured set of destinations select the priority lane.
 */
private[pekko] trait PriorityMessage

/**
 * Failure detector heartbeat messages are marked with this trait.
 */
private[pekko] trait HeartbeatMessage extends PriorityMessage

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[remote] object Remoting {

  final val EndpointManagerName = "endpointManager"

  def localAddressForRemote(
      transportMapping: Map[String, Set[(PekkoProtocolTransport, Address)]],
      remote: Address): Address = {

    transportMapping.get(remote.protocol) match {
      case Some(transports) =>
        val responsibleTransports = transports.filter { case (t, _) => t.isResponsibleFor(remote) }

        responsibleTransports.size match {
          case 0 =>
            throw new RemoteTransportException(
              s"No transport is responsible for address: [$remote] although protocol [${remote.protocol}] is available." +
              " Make sure at least one transport is configured to be responsible for the address.",
              null)

          case 1 =>
            responsibleTransports.head._2

          case _ =>
            throw new RemoteTransportException(
              s"Multiple transports are available for [$remote]: [${responsibleTransports.mkString(",")}]. " +
              "Remoting cannot decide which transport to use to reach the remote system. Change your configuration " +
              "so that only one transport is responsible for the address.",
              null)
        }
      case None =>
        throw new RemoteTransportException(
          s"No transport is loaded for protocol: [${remote.protocol}], available protocols: [${transportMapping.keys
              .mkString(", ")}]",
          null)
    }
  }

  final case class RegisterTransportActor(props: Props, name: String) extends NoSerializationVerificationNeeded

  private[Remoting] class TransportSupervisor extends Actor with RequiresMessageQueue[UnboundedMessageQueueSemantics] {
    override def supervisorStrategy = OneForOneStrategy() {
      case NonFatal(_) => Restart
    }

    def receive = {
      case RegisterTransportActor(props, name) =>
        sender() ! context.actorOf(RARP(context.system).configureDispatcher(props.withDeploy(Deploy.local)), name)
    }
  }

}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
@ccompatUsedUntil213
private[remote] class Remoting(_system: ExtendedActorSystem, _provider: RemoteActorRefProvider)
    extends RemoteTransport(_system, _provider) {

  @volatile private var endpointManager: Option[ActorRef] = None
  @volatile private var transportMapping: Map[String, Set[(PekkoProtocolTransport, Address)]] = _
  // This is effectively a write-once variable similar to a lazy val. The reason for not using a lazy val is exception
  // handling.
  @volatile var addresses: Set[Address] = _
  // This variable has the same semantics as the addresses variable, in the sense it is written once, and emulates
  // a lazy val
  @volatile var defaultAddress: Address = _

  import provider.remoteSettings._

  private implicit val ec: MessageDispatcher = system.dispatchers.lookup(Dispatcher)

  val transportSupervisor = system.systemActorOf(configureDispatcher(Props[TransportSupervisor]()), "transports")

  override def localAddressForRemote(remote: Address): Address =
    Remoting.localAddressForRemote(transportMapping, remote)

  val log: LoggingAdapter = Logging(system.eventStream, classOf[Remoting])
  val eventPublisher = new EventPublisher(system, log, RemoteLifecycleEventsLogLevel)

  private def notifyError(msg: String, cause: Throwable): Unit =
    eventPublisher.notifyListeners(RemotingErrorEvent(new RemoteTransportException(msg, cause)))

  override def shutdown(): Future[Done] = {
    endpointManager match {
      case Some(manager) =>
        implicit val timeout = ShutdownTimeout

        def finalize(): Unit = {
          eventPublisher.notifyListeners(RemotingShutdownEvent)
          endpointManager = None
        }

        (manager ? ShutdownAndFlush)
          .mapTo[Boolean]
          .andThen {
            case Success(flushSuccessful) =>
              if (!flushSuccessful)
                log.warning(
                  "Shutdown finished, but flushing might not have been successful and some messages might have been dropped. " +
                  "Increase pekko.remote.flush-wait-on-shutdown to a larger value to avoid this.")
              finalize()

            case Failure(e) =>
              notifyError("Failure during shutdown of remoting.", e)
              finalize()
          }
          .map { _ =>
            Done
          } // RARP needs only org.apache.pekko.Done, not a boolean
      case None =>
        log.warning("Remoting is not running. Ignoring shutdown attempt.")
        Future.successful(Done)
    }
  }

  // Start assumes that it cannot be followed by another start() without having a shutdown() first
  override def start(): Unit = {
    endpointManager match {
      case None =>
        log.info("Starting remoting")
        val manager: ActorRef = system.systemActorOf(
          configureDispatcher(Props(classOf[EndpointManager], provider.remoteSettings.config, log))
            .withDeploy(Deploy.local),
          Remoting.EndpointManagerName)
        endpointManager = Some(manager)

        try {
          val addressesPromise: Promise[Seq[(PekkoProtocolTransport, Address)]] = Promise()
          manager ! Listen(addressesPromise)

          val transports: Seq[(PekkoProtocolTransport, Address)] =
            Await.result(addressesPromise.future, StartupTimeout.duration)
          if (transports.isEmpty) throw new RemoteTransportException("No transport drivers were loaded.", null)

          val mapping = transports
            .groupBy {
              case (transport, _) => transport.schemeIdentifier
            }
            .map { case (k, v) => k -> v.toSet }
          transportMapping = addProtocolsToMap(mapping)

          defaultAddress = transports.head._2
          addresses = transports.map { _._2 }.toSet

          log.info("Remoting started; listening on addresses :" + addresses.mkString("[", ", ", "]"))

          manager ! StartupFinished
          eventPublisher.notifyListeners(RemotingListenEvent(addresses))

        } catch {
          case e: TimeoutException =>
            notifyError(
              "Startup timed out. This is usually related to actor system host setting or host name resolution misconfiguration.",
              e)
            throw e
          case NonFatal(e) =>
            notifyError("Startup failed", e)
            throw e
        }

      case Some(_) =>
        log.warning("Remoting was already started. Ignoring start attempt.")
    }
  }

  override def send(message: Any, senderOption: OptionVal[ActorRef], recipient: RemoteActorRef): Unit =
    endpointManager match {
      case Some(manager) =>
        manager.tell(Send(message, senderOption, recipient), sender = senderOption.getOrElse(Actor.noSender))
      case None =>
        throw new RemoteTransportExceptionNoStackTrace(
          "Attempted to send remote message but Remoting is not running.",
          null)
    }

  override def managementCommand(cmd: Any): Future[Boolean] = endpointManager match {
    case Some(manager) =>
      implicit val timeout = CommandAckTimeout
      (manager ? ManagementCommand(cmd)).map {
        case ManagementCommandAck(status) => status
        case unexpected                   => throw new IllegalArgumentException(s"Unexpected response type: ${unexpected.getClass}")
      }
    case None =>
      throw new RemoteTransportExceptionNoStackTrace(
        "Attempted to send management command but Remoting is not running.",
        null)
  }

  override def quarantine(remoteAddress: Address, uid: Option[Long], reason: String): Unit = endpointManager match {
    case Some(manager) =>
      manager ! Quarantine(remoteAddress, uid.map(_.toInt))
    case _ =>
      throw new RemoteTransportExceptionNoStackTrace(
        s"Attempted to quarantine address [$remoteAddress] with UID [$uid] but Remoting is not running",
        null)
  }

  private[pekko] def boundAddresses: Map[String, Set[Address]] = {
    transportMapping.map {
      case (scheme, transports) =>
        scheme -> transports.flatMap {
          // Need to do like this for binary compatibility reasons
          case (t, _) => Option(t.boundAddress)
        }
    }
  }

  private def addProtocolsToMap(
      map: Map[String, Set[(PekkoProtocolTransport, Address)]]): Map[String, Set[(PekkoProtocolTransport, Address)]] = {
    if (AcceptProtocolNames.size > 1) {
      map.flatMap { case (protocol, transports) =>
        val tcpProtocol = protocol.endsWith(".tcp")
        AcceptProtocolNames.flatMap { newProtocol =>
          if (tcpProtocol)
            Seq(
              s"$newProtocol.tcp" -> transports,
              s"$newProtocol.ssl.tcp" -> transports
            )
          else
            Seq(newProtocol -> transports)
        }
      }
    } else map
  }
}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[remote] object EndpointManager {

  // Messages between Remoting and EndpointManager
  sealed trait RemotingCommand extends NoSerializationVerificationNeeded
  final case class Listen(addressesPromise: Promise[Seq[(PekkoProtocolTransport, Address)]]) extends RemotingCommand
  case object StartupFinished extends RemotingCommand
  case object ShutdownAndFlush extends RemotingCommand
  @InternalStableApi
  final case class Send(
      message: Any,
      senderOption: OptionVal[ActorRef],
      recipient: RemoteActorRef,
      seqOpt: Option[SeqNo] = None)
      extends RemotingCommand
      with HasSequenceNumber {
    override def toString = s"Remote message $senderOption -> $recipient"

    // This MUST throw an exception to indicate that we attempted to put a nonsequenced message in one of the
    // acknowledged delivery buffers
    def seq = seqOpt.get
  }
  final case class Quarantine(remoteAddress: Address, uid: Option[Int]) extends RemotingCommand
  final case class ManagementCommand(cmd: Any) extends RemotingCommand
  final case class ManagementCommandAck(status: Boolean)

  // Messages internal to EndpointManager
  case object Prune extends NoSerializationVerificationNeeded
  final case class ListensResult(
      addressesPromise: Promise[Seq[(PekkoProtocolTransport, Address)]],
      results: Seq[(PekkoProtocolTransport, Address, Promise[AssociationEventListener])])
      extends NoSerializationVerificationNeeded
  final case class ListensFailure(addressesPromise: Promise[Seq[(PekkoProtocolTransport, Address)]], cause: Throwable)
      extends NoSerializationVerificationNeeded

  // Helper class to store address pairs
  final case class Link(localAddress: Address, remoteAddress: Address)

  final case class ResendState(uid: Int, buffer: AckedReceiveBuffer[Message])

  sealed trait EndpointPolicy {

    /**
     * Indicates that the policy does not contain an active endpoint, but it is a tombstone of a previous failure
     */
    def isTombstone: Boolean
  }
  final case class Pass(endpoint: ActorRef, uid: Option[Int]) extends EndpointPolicy {
    override def isTombstone: Boolean = false
  }
  final case class Gated(timeOfRelease: Deadline) extends EndpointPolicy {
    override def isTombstone: Boolean = true
  }
  final case class Quarantined(uid: Int, timeOfRelease: Deadline) extends EndpointPolicy {
    override def isTombstone: Boolean = true
  }

  // Not threadsafe -- only to be used in HeadActor
  class EndpointRegistry {
    private var addressToRefuseUid = HashMap[Address, (Int, Deadline)]()
    private var addressToWritable = HashMap[Address, EndpointPolicy]()
    private var writableToAddress = HashMap[ActorRef, Address]()
    private var addressToReadonly = HashMap[Address, (ActorRef, Int)]()
    private var readonlyToAddress = HashMap[ActorRef, Address]()

    def registerWritableEndpoint(address: Address, uid: Option[Int], endpoint: ActorRef): ActorRef =
      addressToWritable.get(address) match {
        case Some(Pass(e, _)) =>
          throw new IllegalArgumentException(s"Attempting to overwrite existing endpoint [$e] with [$endpoint]")
        case _ =>
          // note that this overwrites Quarantine marker,
          // but that is ok since we keep the quarantined uid in addressToRefuseUid
          addressToWritable += address -> Pass(endpoint, uid)
          writableToAddress += endpoint -> address
          endpoint
      }

    def registerWritableEndpointUid(remoteAddress: Address, uid: Int): Unit = {
      addressToWritable.get(remoteAddress) match {
        case Some(Pass(ep, _)) => addressToWritable += remoteAddress -> Pass(ep, Some(uid))
        case _                 =>
      }
    }

    def registerWritableEndpointRefuseUid(remoteAddress: Address, refuseUid: Int, timeOfRelease: Deadline): Unit = {
      addressToRefuseUid = addressToRefuseUid.updated(remoteAddress, (refuseUid, timeOfRelease))
    }

    def registerReadOnlyEndpoint(address: Address, endpoint: ActorRef, uid: Int): ActorRef = {
      addressToReadonly += address -> ((endpoint, uid))
      readonlyToAddress += endpoint -> address
      endpoint
    }

    def unregisterEndpoint(endpoint: ActorRef): Unit =
      if (isWritable(endpoint)) {
        val address = writableToAddress(endpoint)
        addressToWritable.get(address) match {
          case Some(policy) if policy.isTombstone => // There is already a tombstone directive, leave it there
          case _                                  => addressToWritable -= address
        }
        writableToAddress -= endpoint
        // leave the refuseUid
      } else if (isReadOnly(endpoint)) {
        addressToReadonly -= readonlyToAddress(endpoint)
        readonlyToAddress -= endpoint
      }

    def addressForWriter(writer: ActorRef): Option[Address] = writableToAddress.get(writer)

    def writableEndpointWithPolicyFor(address: Address): Option[EndpointPolicy] = addressToWritable.get(address)

    def hasWritableEndpointFor(address: Address): Boolean = writableEndpointWithPolicyFor(address) match {
      case Some(_: Pass) => true
      case _             => false
    }

    def readOnlyEndpointFor(address: Address): Option[(ActorRef, Int)] = addressToReadonly.get(address)

    def isWritable(endpoint: ActorRef): Boolean = writableToAddress contains endpoint

    def isReadOnly(endpoint: ActorRef): Boolean = readonlyToAddress contains endpoint

    def isQuarantined(address: Address, uid: Int): Boolean = writableEndpointWithPolicyFor(address) match {
      // timeOfRelease is only used for garbage collection. If an address is still probed, we should report the
      // known fact that it is quarantined.
      case Some(Quarantined(`uid`, _)) => true
      case _                           =>
        addressToRefuseUid.get(address).exists { case (refuseUid, _) => refuseUid == uid }
    }

    def refuseUid(address: Address): Option[Int] = writableEndpointWithPolicyFor(address) match {
      // timeOfRelease is only used for garbage collection. If an address is still probed, we should report the
      // known fact that it is quarantined.
      case Some(Quarantined(uid, _)) => Some(uid)
      case _                         => addressToRefuseUid.get(address).map { case (refuseUid, _) => refuseUid }
    }

    /**
     * Marking an endpoint as failed means that we will not try to connect to the remote system within
     * the gated period but it is ok for the remote system to try to connect to us.
     */
    def markAsFailed(endpoint: ActorRef, timeOfRelease: Deadline): Unit =
      if (isWritable(endpoint)) {
        val address = writableToAddress(endpoint)
        addressToWritable.get(address) match {
          case Some(Quarantined(_, _)) => // don't overwrite Quarantined with Gated
          case Some(Pass(_, _))        =>
            addressToWritable += address -> Gated(timeOfRelease)
            writableToAddress -= endpoint
          case Some(Gated(_)) => // already gated
          case None           =>
            addressToWritable += address -> Gated(timeOfRelease)
            writableToAddress -= endpoint
        }
      } else if (isReadOnly(endpoint)) {
        addressToReadonly -= readonlyToAddress(endpoint)
        readonlyToAddress -= endpoint
      }

    def markAsQuarantined(address: Address, uid: Int, timeOfRelease: Deadline): Unit = {
      addressToWritable += address -> Quarantined(uid, timeOfRelease)
      addressToRefuseUid = addressToRefuseUid.updated(address, (uid, timeOfRelease))
    }

    def removePolicy(address: Address): Unit =
      addressToWritable -= address

    def allEndpoints: collection.Iterable[ActorRef] = writableToAddress.keys ++ readonlyToAddress.keys

    def prune(): Unit = {
      addressToWritable = addressToWritable.collect {
        case entry @ (_, Gated(timeOfRelease)) if timeOfRelease.hasTimeLeft() =>
          // Gated removed when no time left
          entry
        case entry @ (_, Quarantined(_, timeOfRelease)) if timeOfRelease.hasTimeLeft() =>
          // Quarantined removed when no time left
          entry
        case entry @ (_, _: Pass) => entry
      }

      addressToRefuseUid = addressToRefuseUid.collect {
        case entry @ (_, (_, timeOfRelease)) if timeOfRelease.hasTimeLeft() =>
          // // Quarantined/refuseUid removed when no time left
          entry
      }
    }
  }
}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[remote] class EndpointManager(conf: Config, log: LoggingAdapter)
    extends Actor
    with RequiresMessageQueue[UnboundedMessageQueueSemantics] {

  import EndpointManager._
  import context.dispatcher

  val settings = new RemoteSettings(conf)
  val extendedSystem = context.system.asInstanceOf[ExtendedActorSystem]
  val endpointId: Iterator[Int] = Iterator.from(0)

  val eventPublisher = new EventPublisher(context.system, log, settings.RemoteLifecycleEventsLogLevel)

  // Mapping between addresses and endpoint actors. If passive connections are turned off, incoming connections
  // will be not part of this map!
  val endpoints = new EndpointRegistry
  // Mapping between transports and the local addresses they listen to
  var transportMapping: Map[Address, PekkoProtocolTransport] = Map()

  val pruneInterval: FiniteDuration = (settings.RetryGateClosedFor * 2).max(1.second).min(10.seconds)

  val pruneTimerCancellable: Cancellable =
    context.system.scheduler.scheduleWithFixedDelay(pruneInterval, pruneInterval, self, Prune)

  var pendingReadHandoffs = Map[ActorRef, PekkoProtocolHandle]()
  var stashedInbound = Map[ActorRef, Vector[InboundAssociation]]()

  def handleStashedInbound(endpoint: ActorRef, writerIsIdle: Boolean): Unit = {
    val stashed = stashedInbound.getOrElse(endpoint, Vector.empty)
    stashedInbound -= endpoint
    stashed.foreach(handleInboundAssociation(_, writerIsIdle))
  }

  def keepQuarantinedOr(remoteAddress: Address)(body: => Unit): Unit = endpoints.refuseUid(remoteAddress) match {
    case Some(uid) =>
      log.info(
        "Quarantined address [{}] is still unreachable or has not been restarted. Keeping it quarantined.",
        remoteAddress)
      // Restoring Quarantine marker overwritten by a Pass(endpoint, refuseUid) pair while probing remote system.
      endpoints.markAsQuarantined(remoteAddress, uid, Deadline.now + settings.QuarantineDuration)
    case None => body
  }

  override val supervisorStrategy = {
    def hopeless(e: HopelessAssociation): SupervisorStrategy.Directive = e match {
      case HopelessAssociation(_, remoteAddress, Some(uid), reason) =>
        log.error(
          reason,
          "Association to [{}] with UID [{}] irrecoverably failed. Quarantining address.",
          remoteAddress,
          uid)
        settings.QuarantineDuration match {
          case d: FiniteDuration =>
            endpoints.markAsQuarantined(remoteAddress, uid, Deadline.now + d)
            eventPublisher.notifyListeners(QuarantinedEvent(remoteAddress, uid.toLong))
          case null => // disabled
        }
        Stop

      case HopelessAssociation(_, remoteAddress, None, _) =>
        keepQuarantinedOr(remoteAddress) {
          log.warning(
            "Association to [{}] with unknown UID is irrecoverably failed. " +
            "Address cannot be quarantined without knowing the UID, gating instead for {} ms.",
            remoteAddress,
            settings.RetryGateClosedFor.toMillis)
          endpoints.markAsFailed(sender(), Deadline.now + settings.RetryGateClosedFor)
        }
        Stop
    }

    OneForOneStrategy(loggingEnabled = false) {
      case InvalidAssociation(localAddress, remoteAddress, reason, disassociationInfo) =>
        keepQuarantinedOr(remoteAddress) {
          val causedBy = if (reason.getCause == null) "" else s"Caused by: [${reason.getCause.getMessage}]"
          log.warning(
            "Tried to associate with unreachable remote address [{}]. " +
            "Address is now gated for {} ms, all messages to this address will be delivered to dead letters. " +
            "Reason: [{}] {}",
            remoteAddress,
            settings.RetryGateClosedFor.toMillis,
            reason.getMessage,
            causedBy)
          endpoints.markAsFailed(sender(), Deadline.now + settings.RetryGateClosedFor)
        }
        disassociationInfo.foreach {
          case AssociationHandle.Quarantined =>
            context.system.eventStream.publish(ThisActorSystemQuarantinedEvent(localAddress, remoteAddress))
          case _ => // do nothing
        }
        Stop

      case ShutDownAssociation(_, remoteAddress, _) =>
        keepQuarantinedOr(remoteAddress) {
          log.debug(
            "Remote system with address [{}] has shut down. " +
            "Address is now gated for {} ms, all messages to this address will be delivered to dead letters.",
            remoteAddress,
            settings.RetryGateClosedFor.toMillis)
          endpoints.markAsFailed(sender(), Deadline.now + settings.RetryGateClosedFor)
        }
        Stop

      case e: HopelessAssociation =>
        hopeless(e)

      case e: ActorInitializationException if e.getCause.isInstanceOf[HopelessAssociation] =>
        hopeless(e.getCause.asInstanceOf[HopelessAssociation])

      case NonFatal(e) =>
        e match {
          case _: EndpointDisassociatedException | _: EndpointAssociationException => // no logging
          case _                                                                   => log.error(e, e.getMessage)
        }
        endpoints.markAsFailed(sender(), Deadline.now + settings.RetryGateClosedFor)
        Stop
    }
  }

  // Structure for saving reliable delivery state across restarts of Endpoints
  val receiveBuffers = new ConcurrentHashMap[Link, ResendState]()

  def receive = {
    case Listen(addressesPromise) =>
      listens
        .map { ListensResult(addressesPromise, _) }
        .recover {
          case NonFatal(e) => ListensFailure(addressesPromise, e)
        }
        .pipeTo(self)
    case ListensResult(addressesPromise, results) =>
      transportMapping = results
        .groupBy {
          case (_, transportAddress, _) => transportAddress
        }
        .map {
          case (a, t) if t.size > 1 =>
            throw new RemoteTransportException(
              s"There are more than one transports listening on local address [$a]",
              null)
          case (a, t) => a -> t.head._1
        }
      // Register to each transport as listener and collect mapping to addresses
      val transportsAndAddresses = results.map {
        case (transport, address, promise) =>
          promise.success(ActorAssociationEventListener(self))
          transport -> address
      }
      addressesPromise.success(transportsAndAddresses)
    case ListensFailure(addressesPromise, cause) =>
      addressesPromise.failure(cause)
    case ia: InboundAssociation =>
      context.system.scheduler.scheduleOnce(10.milliseconds, self, ia)
    case ManagementCommand(_) =>
      sender() ! ManagementCommandAck(status = false)
    case StartupFinished =>
      context.become(accepting)
    case ShutdownAndFlush =>
      sender() ! true
      context.stop(self) // Nothing to flush at this point
  }

  val accepting: Receive = {
    case ManagementCommand(cmd) =>
      val allStatuses: immutable.Seq[Future[Boolean]] =
        transportMapping.values.iterator.map(transport => transport.managementCommand(cmd)).to(immutable.IndexedSeq)
      pekko.compat.Future.fold(allStatuses)(true)(_ && _).map(ManagementCommandAck.apply).pipeTo(sender())

    case Quarantine(address, uidToQuarantineOption) =>
      // Stop writers
      (endpoints.writableEndpointWithPolicyFor(address), uidToQuarantineOption) match {
        case (Some(Pass(endpoint, _)), None) =>
          context.stop(endpoint)
          log.warning(
            "Association to [{}] with unknown UID is reported as quarantined, but " +
            "address cannot be quarantined without knowing the UID, gating instead for {} ms.",
            address,
            settings.RetryGateClosedFor.toMillis)
          endpoints.markAsFailed(endpoint, Deadline.now + settings.RetryGateClosedFor)
        case (Some(Pass(endpoint, uidOption)), Some(quarantineUid)) =>
          uidOption match {
            case Some(`quarantineUid`) =>
              endpoints.markAsQuarantined(address, quarantineUid, Deadline.now + settings.QuarantineDuration)
              eventPublisher.notifyListeners(QuarantinedEvent(address, quarantineUid.toLong))
              context.stop(endpoint)
            // or it does not match with the UID to be quarantined
            case None if !endpoints.refuseUid(address).contains(quarantineUid) =>
              // the quarantine uid may be got fresh by cluster gossip, so update refuseUid for late handle when the writer got uid
              endpoints.registerWritableEndpointRefuseUid(
                address,
                quarantineUid,
                Deadline.now + settings.QuarantineDuration)
            case _ => // the quarantine uid has lost the race with some failure, do nothing
          }
        case (Some(Quarantined(uid, _)), Some(quarantineUid)) if uid == quarantineUid => // the UID to be quarantined already exists, do nothing
        case (_, Some(quarantineUid))                                                 =>
          // the current state is gated or quarantined, and we know the UID, update
          endpoints.markAsQuarantined(address, quarantineUid, Deadline.now + settings.QuarantineDuration)
          eventPublisher.notifyListeners(QuarantinedEvent(address, quarantineUid.toLong))
        case _ => // the current state is Gated, WasGated or Quarantined, and we don't know the UID, do nothing.
      }

      // Stop inbound read-only associations
      (endpoints.readOnlyEndpointFor(address), uidToQuarantineOption) match {
        case (Some((endpoint, _)), None)                                                        => context.stop(endpoint)
        case (Some((endpoint, currentUid)), Some(quarantineUid)) if currentUid == quarantineUid =>
          context.stop(endpoint)
        case _ => // nothing to stop
      }

      def matchesQuarantine(handle: PekkoProtocolHandle): Boolean = {
        handle.remoteAddress == address &&
        uidToQuarantineOption.forall(_ == handle.handshakeInfo.uid)
      }

      // Stop all matching pending read handoffs
      pendingReadHandoffs = pendingReadHandoffs.filter {
        case (pendingActor, pendingHandle) =>
          val drop = matchesQuarantine(pendingHandle)
          // Side-effecting here
          if (drop) {
            pendingHandle.disassociate("the pending handle was quarantined", log)
            context.stop(pendingActor)
          }
          !drop
      }

      // Stop all matching stashed connections
      stashedInbound = stashedInbound.map {
        case (writer, associations) =>
          writer -> associations.filter { assoc =>
            val handle = assoc.association.asInstanceOf[PekkoProtocolHandle]
            val drop = matchesQuarantine(handle)
            if (drop) handle.disassociate("the stashed inbound handle was quarantined", log)
            !drop
          }
      }

    case s @ Send(_, _, recipientRef, _) =>
      val recipientAddress = recipientRef.path.address

      def createAndRegisterWritingEndpoint(): ActorRef = {
        endpoints.registerWritableEndpoint(
          recipientAddress,
          uid = None,
          createEndpoint(
            recipientAddress,
            recipientRef.localAddressToUse,
            transportMapping(recipientRef.localAddressToUse),
            settings,
            handleOption = None,
            writing = true))
      }

      endpoints.writableEndpointWithPolicyFor(recipientAddress) match {
        case Some(Pass(endpoint, _)) =>
          endpoint ! s
        case Some(Gated(timeOfRelease)) =>
          if (timeOfRelease.isOverdue()) createAndRegisterWritingEndpoint() ! s
          else extendedSystem.deadLetters ! s
        case Some(Quarantined(_, _)) =>
          // timeOfRelease is only used for garbage collection reasons, therefore it is ignored here. We still have
          // the Quarantined tombstone and we know what UID we don't want to accept, so use it.
          createAndRegisterWritingEndpoint() ! s
        case None =>
          createAndRegisterWritingEndpoint() ! s

      }

    case ia @ InboundAssociation(_: PekkoProtocolHandle) =>
      handleInboundAssociation(ia, writerIsIdle = false)
    case EndpointWriter.StoppedReading(endpoint) =>
      acceptPendingReader(takingOverFrom = endpoint)
    case Terminated(endpoint) =>
      acceptPendingReader(takingOverFrom = endpoint)
      endpoints.unregisterEndpoint(endpoint)
      handleStashedInbound(endpoint, writerIsIdle = false)
    case EndpointWriter.TookOver(endpoint, handle) =>
      removePendingReader(takingOverFrom = endpoint, withHandle = handle)
    case ReliableDeliverySupervisor.GotUid(uid, remoteAddress) =>
      val refuseUidOption = endpoints.refuseUid(remoteAddress)
      endpoints.writableEndpointWithPolicyFor(remoteAddress) match {
        case Some(Pass(endpoint, _)) =>
          if (refuseUidOption.contains(uid)) {
            endpoints.markAsQuarantined(remoteAddress, uid, Deadline.now + settings.QuarantineDuration)
            eventPublisher.notifyListeners(QuarantinedEvent(remoteAddress, uid.toLong))
            context.stop(endpoint)
          } else endpoints.registerWritableEndpointUid(remoteAddress, uid)
          handleStashedInbound(sender(), writerIsIdle = false)
        case _ => // the GotUid might have lost the race with some failure
      }
    case ReliableDeliverySupervisor.Idle =>
      handleStashedInbound(sender(), writerIsIdle = true)
    case Prune =>
      endpoints.prune()
    case ShutdownAndFlush =>
      // Shutdown all endpoints and signal to sender() when ready (and whether all endpoints were shut down gracefully)

      @nowarn("msg=deprecated")
      def shutdownAll[T](resources: IterableOnce[T])(shutdown: T => Future[Boolean]): Future[Boolean] = {
        Future.sequence(resources.toList.map(shutdown)).map(_.forall(identity)).recover {
          case NonFatal(_) => false
        }
      }

      (for {
        // The construction of the future for shutdownStatus has to happen after the flushStatus future has been finished
        // so that endpoints are shut down before transports.
        flushStatus <- shutdownAll(endpoints.allEndpoints)(
          gracefulStop(_, settings.FlushWait, EndpointWriter.FlushAndStop))
        shutdownStatus <- shutdownAll(transportMapping.values)(_.shutdown())
      } yield flushStatus && shutdownStatus).pipeTo(sender())

      pendingReadHandoffs.valuesIterator.foreach(_.disassociate(AssociationHandle.Shutdown))

      // Ignore all other writes
      normalShutdown = true
      context.become(flushing)
  }

  def flushing: Receive = {
    case s: Send                                    => extendedSystem.deadLetters ! s
    case InboundAssociation(h: PekkoProtocolHandle) => h.disassociate(AssociationHandle.Shutdown)
    case Terminated(_)                              => // why should we care now?
  }

  def handleInboundAssociation(ia: InboundAssociation, writerIsIdle: Boolean): Unit = ia match {
    case ia @ InboundAssociation(handle: PekkoProtocolHandle) =>
      endpoints.readOnlyEndpointFor(handle.remoteAddress) match {
        case Some((endpoint, _)) =>
          pendingReadHandoffs
            .get(endpoint)
            .foreach(_.disassociate("the existing readOnly association was replaced by a new incoming one", log))
          pendingReadHandoffs += endpoint -> handle
          endpoint ! EndpointWriter.TakeOver(handle, self)
          endpoints.writableEndpointWithPolicyFor(handle.remoteAddress) match {
            case Some(Pass(ep, _)) => ep ! ReliableDeliverySupervisor.Ungate
            case _                 =>
          }
        case None =>
          if (endpoints.isQuarantined(handle.remoteAddress, handle.handshakeInfo.uid))
            handle.disassociate(AssociationHandle.Quarantined)
          else
            endpoints.writableEndpointWithPolicyFor(handle.remoteAddress) match {
              case Some(Pass(ep, None)) =>
                // Idle writer will never send a GotUid or a Terminated so we need to "provoke it"
                // to get an unstash event
                if (!writerIsIdle) {
                  ep ! ReliableDeliverySupervisor.IsIdle
                  stashedInbound += ep -> (stashedInbound.getOrElse(ep, Vector.empty) :+ ia)
                } else
                  createAndRegisterEndpoint(handle)
              case Some(Pass(ep, Some(uid))) =>
                if (handle.handshakeInfo.uid == uid) {
                  pendingReadHandoffs
                    .get(ep)
                    .foreach(
                      _.disassociate("the existing writable association was replaced by a new incoming one", log))
                  pendingReadHandoffs += ep -> handle
                  ep ! EndpointWriter.StopReading(ep, self)
                  ep ! ReliableDeliverySupervisor.Ungate
                } else {
                  context.stop(ep)
                  endpoints.unregisterEndpoint(ep)
                  pendingReadHandoffs -= ep
                  endpoints.markAsQuarantined(handle.remoteAddress, uid, Deadline.now + settings.QuarantineDuration)
                  createAndRegisterEndpoint(handle)
                }
              case _ =>
                createAndRegisterEndpoint(handle)
            }
      }
    case _ => // ignore
  }

  private def createAndRegisterEndpoint(handle: PekkoProtocolHandle): Unit = {
    val writing = settings.UsePassiveConnections && !endpoints.hasWritableEndpointFor(handle.remoteAddress)
    eventPublisher.notifyListeners(AssociatedEvent(handle.localAddress, handle.remoteAddress, inbound = true))

    val endpoint = createEndpoint(
      handle.remoteAddress,
      handle.localAddress,
      transportMapping(handle.localAddress),
      settings,
      Some(handle),
      writing)

    if (writing)
      endpoints.registerWritableEndpoint(handle.remoteAddress, Some(handle.handshakeInfo.uid), endpoint)
    else {
      endpoints.registerReadOnlyEndpoint(handle.remoteAddress, endpoint, handle.handshakeInfo.uid)
      if (!endpoints.hasWritableEndpointFor(handle.remoteAddress))
        endpoints.removePolicy(handle.remoteAddress)
    }
  }

  private def listens: Future[Seq[(PekkoProtocolTransport, Address, Promise[AssociationEventListener])]] = {
    /*
     * Constructs chains of adapters on top of each driver as given in configuration. The resulting structure looks
     * like the following:
     *   PekkoProtocolTransport <- Adapter <- ... <- Adapter <- Driver
     *
     * The transports variable contains only the heads of each chains (the PekkoProtocolTransport instances).
     */
    val transports: Seq[PekkoProtocolTransport] = for ((fqn, adapters, config) <- settings.Transports) yield {

      val args = Seq(classOf[ExtendedActorSystem] -> context.system, classOf[Config] -> config)

      // Loads the driver -- the bottom element of the chain.
      // The chain at this point:
      //   Driver
      val driver = extendedSystem.dynamicAccess
        .createInstanceFor[Transport](fqn, args)
        .recover {

          case exception =>
            throw new IllegalArgumentException(
              s"Cannot instantiate transport [$fqn]. " +
              "Make sure it extends [org.apache.pekko.remote.transport.Transport] and has constructor with " +
              "[org.apache.pekko.actor.ExtendedActorSystem] and [com.typesafe.config.Config] parameters",
              exception)

        }
        .get

      // Iteratively decorates the bottom level driver with a list of adapters.
      // The chain at this point:
      //   Adapter <- ... <- Adapter <- Driver
      val wrappedTransport =
        adapters.map { TransportAdaptersExtension.get(context.system).getAdapterProvider }.foldLeft(driver) {
          (t: Transport, provider: TransportAdapterProvider) =>
            // The TransportAdapterProvider will wrap the given Transport and returns with a wrapped one
            provider.create(t, context.system.asInstanceOf[ExtendedActorSystem])
        }

      // Apply PekkoProtocolTransport wrapper to the end of the chain
      // The chain at this point:
      //   PekkoProtocolTransport <- Adapter <- ... <- Adapter <- Driver
      new PekkoProtocolTransport(wrappedTransport, context.system, new PekkoProtocolSettings(conf),
        PekkoPduProtobufCodec)
    }

    // Collect all transports, listen addresses and listener promises in one future
    Future.sequence(transports.map { transport =>
      transport.listen.map { case (address, listenerPromise) => (transport, address, listenerPromise) }
    })
  }

  private def acceptPendingReader(takingOverFrom: ActorRef): Unit = {
    if (pendingReadHandoffs.contains(takingOverFrom)) {
      val handle = pendingReadHandoffs(takingOverFrom)
      pendingReadHandoffs -= takingOverFrom
      eventPublisher.notifyListeners(AssociatedEvent(handle.localAddress, handle.remoteAddress, inbound = true))

      val endpoint = createEndpoint(
        handle.remoteAddress,
        handle.localAddress,
        transportMapping(handle.localAddress),
        settings,
        Some(handle),
        writing = false)
      endpoints.registerReadOnlyEndpoint(handle.remoteAddress, endpoint, handle.handshakeInfo.uid)
    }
  }

  private def removePendingReader(takingOverFrom: ActorRef, withHandle: PekkoProtocolHandle): Unit = {
    if (pendingReadHandoffs.get(takingOverFrom).exists(handle => handle == withHandle))
      pendingReadHandoffs -= takingOverFrom
  }

  private def createEndpoint(
      remoteAddress: Address,
      localAddress: Address,
      transport: PekkoProtocolTransport,
      endpointSettings: RemoteSettings,
      handleOption: Option[PekkoProtocolHandle],
      writing: Boolean): ActorRef = {
    require(transportMapping contains localAddress, "Transport mapping is not defined for the address")
    // refuseUid is ignored for read-only endpoints since the UID of the remote system is already known and has passed
    // quarantine checks
    val refuseUid = endpoints.refuseUid(remoteAddress)

    if (writing)
      context.watch(
        context.actorOf(
          RARP(extendedSystem)
            .configureDispatcher(
              ReliableDeliverySupervisor.props(
                handleOption,
                localAddress,
                remoteAddress,
                refuseUid,
                transport,
                endpointSettings,
                PekkoPduProtobufCodec,
                receiveBuffers))
            .withDeploy(Deploy.local),
          "reliableEndpointWriter-" + AddressUrlEncoder(remoteAddress) + "-" + endpointId.next()))
    else
      context.watch(
        context.actorOf(
          RARP(extendedSystem)
            .configureDispatcher(
              EndpointWriter.props(
                handleOption,
                localAddress,
                remoteAddress,
                refuseUid,
                transport,
                endpointSettings,
                PekkoPduProtobufCodec,
                receiveBuffers,
                reliableDeliverySupervisor = None))
            .withDeploy(Deploy.local),
          "endpointWriter-" + AddressUrlEncoder(remoteAddress) + "-" + endpointId.next()))
  }

  private var normalShutdown = false

  override def postStop(): Unit = {
    pruneTimerCancellable.cancel()
    pendingReadHandoffs.valuesIterator.foreach(_.disassociate(AssociationHandle.Shutdown))

    if (!normalShutdown) {
      // Remaining running endpoints are children, so they will clean up themselves.
      // We still need to clean up any remaining transports because handles might be in mailboxes, and for example
      // Netty is not part of the actor hierarchy, so its handles will not be cleaned up if no actor is taking
      // responsibility of them (because they are sitting in a mailbox).
      log.error("Remoting system has been terminated abrubtly. Attempting to shut down transports")
      // The result of this shutdown is async, should we try to Await for a short duration?
      transportMapping.values.map(_.shutdown())
    }
  }

}
