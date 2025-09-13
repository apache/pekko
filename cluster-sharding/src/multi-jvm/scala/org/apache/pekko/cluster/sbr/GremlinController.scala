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

package org.apache.pekko.cluster.sbr

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, ActorRef, Address, ExtendedActorSystem, Props }
import pekko.cluster.Cluster
import pekko.pattern.pipe
import pekko.remote.RemoteActorRefProvider
import pekko.remote.transport.ThrottlerTransportAdapter.{ Blackhole, Direction, SetThrottle, Unthrottled }
import pekko.serialization.jackson.CborSerializable

object GremlinController {
  final case class BlackholeNode(target: Address) extends CborSerializable
  final case class PassThroughNode(target: Address) extends CborSerializable
  case object GetAddress extends CborSerializable

  def props: Props =
    Props(new GremlinController)
}

class GremlinController extends Actor with ActorLogging {
  import context.dispatcher

  import GremlinController._
  val transport =
    context.system.asInstanceOf[ExtendedActorSystem].provider.asInstanceOf[RemoteActorRefProvider].transport
  val selfAddress = Cluster(context.system).selfAddress

  override def receive = {
    case GetAddress =>
      sender() ! selfAddress
    case BlackholeNode(target) =>
      log.debug("Blackhole {} <-> {}", selfAddress, target)
      transport.managementCommand(SetThrottle(target, Direction.Both, Blackhole)).pipeTo(sender())
    case PassThroughNode(target) =>
      log.debug("PassThrough {} <-> {}", selfAddress, target)
      transport.managementCommand(SetThrottle(target, Direction.Both, Unthrottled)).pipeTo(sender())
  }
}

object GremlinControllerProxy {
  def props(target: ActorRef): Props =
    Props(new GremlinControllerProxy(target))
}

class GremlinControllerProxy(target: ActorRef) extends Actor {
  override def receive = {
    case msg => target.forward(msg)
  }
}
