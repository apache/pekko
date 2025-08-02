/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.coordination.lease

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
import pekko.actor.Props
import pekko.coordination.lease.scaladsl.Lease
import pekko.event.Logging
import pekko.pattern.ask
import pekko.testkit.JavaSerializable
import pekko.util.Timeout

object TestLeaseActor {
  def props(): Props =
    Props(new TestLeaseActor)

  sealed trait LeaseRequest extends JavaSerializable
  final case class Acquire(owner: String) extends LeaseRequest
  final case class Release(owner: String) extends LeaseRequest
  final case class Create(leaseName: String, ownerName: String) extends JavaSerializable

  case object GetRequests extends JavaSerializable
  final case class LeaseRequests(requests: List[LeaseRequest]) extends JavaSerializable
  final case class ActionRequest(request: LeaseRequest, result: Any) extends JavaSerializable // boolean of Failure
}

class TestLeaseActor extends Actor with ActorLogging {
  import TestLeaseActor._

  var requests: List[(ActorRef, LeaseRequest)] = Nil

  override def receive = {

    case c: Create =>
      log.info("Lease created with name {} ownerName {}", c.leaseName, c.ownerName)

    case request: LeaseRequest =>
      log.info("Lease request {} from {}", request, sender())
      requests = (sender(), request) :: requests

    case GetRequests =>
      sender() ! LeaseRequests(requests.map(_._2))

    case ActionRequest(request, result) =>
      requests.find(_._2 == request) match {
        case Some((snd, req)) =>
          log.info("Actioning request {} to {}", req, result)
          snd ! result
          requests = requests.filterNot(_._2 == request)
        case None =>
          throw new RuntimeException(s"unknown request to action: $request. Requests: $requests")
      }

  }

}

object TestLeaseActorClientExt extends ExtensionId[TestLeaseActorClientExt] with ExtensionIdProvider {
  override def get(system: ActorSystem): TestLeaseActorClientExt = super.get(system)
  override def get(system: ClassicActorSystemProvider): TestLeaseActorClientExt = super.get(system)
  override def lookup = TestLeaseActorClientExt
  override def createExtension(system: ExtendedActorSystem): TestLeaseActorClientExt =
    new TestLeaseActorClientExt(system)
}

class TestLeaseActorClientExt(val system: ExtendedActorSystem) extends Extension {

  private val leaseActor = new AtomicReference[ActorRef]()

  def getLeaseActor(): ActorRef = {
    val lease = leaseActor.get
    if (lease == null) throw new IllegalStateException("LeaseActorRef must be set first")
    lease
  }

  def setActorLease(client: ActorRef): Unit =
    leaseActor.set(client)

}

class TestLeaseActorClient(settings: LeaseSettings, system: ExtendedActorSystem) extends Lease(settings) {
  import TestLeaseActor.Acquire
  import TestLeaseActor.Create
  import TestLeaseActor.Release

  private val log = Logging(system, classOf[TestLeaseActorClient])
  val leaseActor = TestLeaseActorClientExt(system).getLeaseActor()

  log.info("lease created {}", settings)
  leaseActor ! Create(settings.leaseName, settings.ownerName)

  private implicit val timeout: Timeout = Timeout(100.seconds)

  override def acquire(): Future[Boolean] = {
    (leaseActor ? Acquire(settings.ownerName)).mapTo[Boolean]
  }

  override def release(): Future[Boolean] = {
    (leaseActor ? Release(settings.ownerName)).mapTo[Boolean]
  }

  override def checkLease(): Boolean = false

  override def acquire(callback: Option[Throwable] => Unit): Future[Boolean] =
    (leaseActor ? Acquire(settings.ownerName)).mapTo[Boolean]
}
