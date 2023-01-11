/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sbr

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
import pekko.actor.Props
import pekko.coordination.lease.LeaseSettings
import pekko.coordination.lease.scaladsl.Lease
import pekko.pattern.ask
import pekko.serialization.jackson.CborSerializable
import pekko.util.Timeout

object SbrTestLeaseActor {
  def props: Props =
    Props(new SbrTestLeaseActor)

  final case class Acquire(owner: String) extends CborSerializable
  final case class Release(owner: String) extends CborSerializable
}

class SbrTestLeaseActor extends Actor with ActorLogging {
  import SbrTestLeaseActor._

  var owner: Option[String] = None

  override def receive = {
    case Acquire(o) =>
      owner match {
        case None =>
          log.info("ActorLease: acquired by [{}]", o)
          owner = Some(o)
          sender() ! true
        case Some(`o`) =>
          log.info("ActorLease: renewed by [{}]", o)
          sender() ! true
        case Some(existingOwner) =>
          log.info("ActorLease: requested by [{}], but already held by [{}]", o, existingOwner)
          sender() ! false
      }

    case Release(o) =>
      owner match {
        case None =>
          log.info("ActorLease: released by [{}] but no owner", o)
          owner = Some(o)
          sender() ! true
        case Some(`o`) =>
          log.info("ActorLease: released by [{}]", o)
          sender() ! true
        case Some(existingOwner) =>
          log.info("ActorLease: release attempt by [{}], but held by [{}]", o, existingOwner)
          sender() ! false
      }
  }

}

object SbrTestLeaseActorClientExt extends ExtensionId[SbrTestLeaseActorClientExt] with ExtensionIdProvider {
  override def get(system: ActorSystem): SbrTestLeaseActorClientExt = super.get(system)
  override def lookup = SbrTestLeaseActorClientExt
  override def createExtension(system: ExtendedActorSystem): SbrTestLeaseActorClientExt =
    new SbrTestLeaseActorClientExt(system)
}

class SbrTestLeaseActorClientExt(val system: ExtendedActorSystem) extends Extension {

  private val leaseClient = new AtomicReference[SbrTestLeaseActorClient]()

  def getActorLeaseClient(): SbrTestLeaseActorClient = {
    val lease = leaseClient.get
    if (lease == null) throw new IllegalStateException("ActorLeaseClient must be set first")
    lease
  }

  def setActorLeaseClient(client: SbrTestLeaseActorClient): Unit =
    leaseClient.set(client)

}

class SbrTestLeaseActorClient(settings: LeaseSettings, system: ExtendedActorSystem) extends Lease(settings) {
  import SbrTestLeaseActor.Acquire
  import SbrTestLeaseActor.Release

  SbrTestLeaseActorClientExt(system).setActorLeaseClient(this)

  private implicit val timeout: Timeout = Timeout(3.seconds)

  private val _leaseRef = new AtomicReference[ActorRef]

  private def leaseRef: ActorRef = {
    val ref = _leaseRef.get
    if (ref == null) throw new IllegalStateException("ActorLeaseRef must be set first")
    ref
  }

  def setActorLeaseRef(ref: ActorRef): Unit =
    _leaseRef.set(ref)

  override def acquire(): Future[Boolean] = {
    (leaseRef ? Acquire(settings.ownerName)).mapTo[Boolean]
  }

  override def acquire(leaseLostCallback: Option[Throwable] => Unit): Future[Boolean] =
    acquire()

  override def release(): Future[Boolean] = {
    (leaseRef ? Release(settings.ownerName)).mapTo[Boolean]
  }

  override def checkLease(): Boolean = false
}
