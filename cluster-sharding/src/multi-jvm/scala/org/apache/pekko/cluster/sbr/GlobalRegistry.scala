/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sbr

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.ActorRef
import pekko.actor.Address
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.sharding.ShardRegion
import pekko.serialization.jackson.CborSerializable

object GlobalRegistry {
  final case class Register(key: String, address: Address) extends CborSerializable
  final case class Unregister(key: String, address: Address) extends CborSerializable
  final case class DoubleRegister(key: String, msg: String) extends CborSerializable

  def props(probe: ActorRef, onlyErrors: Boolean): Props =
    Props(new GlobalRegistry(probe, onlyErrors))

  object SingletonActor {
    def props(registry: ActorRef): Props =
      Props(new SingletonActor(registry))

    val extractEntityId: ShardRegion.ExtractEntityId = {
      case id: Int => (id.toString, id)
    }

    val extractShardId: ShardRegion.ExtractShardId = {
      case id: Int => (id % 10).toString
      case _       => throw new IllegalArgumentException()
    }
  }

  class SingletonActor(registry: ActorRef) extends Actor with ActorLogging {
    val key = self.path.toStringWithoutAddress + "-" + Cluster(context.system).selfDataCenter

    override def preStart(): Unit = {
      log.info("Starting")
      registry ! Register(key, Cluster(context.system).selfAddress)
    }

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      // don't call postStop
    }

    override def postStop(): Unit = {
      log.info("Stopping")
      registry ! Unregister(key, Cluster(context.system).selfAddress)
    }

    override def receive = {
      case i: Int => sender() ! i
    }
  }
}

class GlobalRegistry(probe: ActorRef, onlyErrors: Boolean) extends Actor with ActorLogging {
  import GlobalRegistry._

  var registry = Map.empty[String, Address]
  var unregisterTimestamp = Map.empty[String, Long]

  override def receive = {
    case r @ Register(key, address) =>
      log.info("{}", r)
      if (registry.contains(key)) {
        val errMsg = s"trying to register $address, but ${registry(key)} was already registered for $key"
        log.error(errMsg)
        probe ! DoubleRegister(key, errMsg)
      } else {
        unregisterTimestamp.get(key).foreach { t =>
          log.info("Unregister/register margin for [{}] was [{}] ms", key, (System.nanoTime() - t).nanos.toMillis)
        }
        registry += key -> address
        if (!onlyErrors) probe ! r
      }

    case u @ Unregister(key, address) =>
      log.info("{}", u)
      if (!registry.contains(key))
        probe ! s"$key was not registered"
      else if (registry(key) != address)
        probe ! s"${registry(key)} instead of $address was registered for $key"
      else {
        registry -= key
        unregisterTimestamp += key -> System.nanoTime()
        if (!onlyErrors) probe ! u
      }
  }

}
