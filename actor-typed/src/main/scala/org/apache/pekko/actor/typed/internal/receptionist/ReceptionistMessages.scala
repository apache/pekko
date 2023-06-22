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

package org.apache.pekko.actor.typed.internal.receptionist

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.receptionist.{ Receptionist, ServiceKey }
import pekko.actor.typed.receptionist.Receptionist.Command
import pekko.annotation.InternalApi
import pekko.util.ccompat.JavaConverters._

/**
 * Internal API
 *
 * Shared message implementations for local and cluster receptionist
 */
@InternalApi
private[pekko] object ReceptionistMessages {
  // some trixery here to provide a nice _and_ safe API in the face
  // of type erasure, more type safe factory methods for each message
  // is the user API below while still hiding the type parameter so that
  // users don't incorrectly match against it
  final case class Register[T] private[pekko] (
      key: ServiceKey[T],
      serviceInstance: ActorRef[T],
      replyTo: Option[ActorRef[Receptionist.Registered]])
      extends Command

  final case class Deregister[T] private[pekko] (
      key: ServiceKey[T],
      serviceInstance: ActorRef[T],
      replyTo: Option[ActorRef[Receptionist.Deregistered]])
      extends Command

  final case class Registered[T] private[pekko] (key: ServiceKey[T], _serviceInstance: ActorRef[T])
      extends Receptionist.Registered {
    def isForKey(key: ServiceKey[_]): Boolean = key == this.key
    def serviceInstance[M](key: ServiceKey[M]): ActorRef[M] = {
      if (key != this.key)
        throw new IllegalArgumentException(s"Wrong key [$key] used, must use listing key [${this.key}]")
      _serviceInstance.asInstanceOf[ActorRef[M]]
    }

    def getServiceInstance[M](key: ServiceKey[M]): ActorRef[M] =
      serviceInstance(key)
  }

  final case class Deregistered[T] private[pekko] (key: ServiceKey[T], _serviceInstance: ActorRef[T])
      extends Receptionist.Deregistered {
    def isForKey(key: ServiceKey[_]): Boolean = key == this.key
    def serviceInstance[M](key: ServiceKey[M]): ActorRef[M] = {
      if (key != this.key)
        throw new IllegalArgumentException(s"Wrong key [$key] used, must use listing key [${this.key}]")
      _serviceInstance.asInstanceOf[ActorRef[M]]
    }

    def getServiceInstance[M](key: ServiceKey[M]): ActorRef[M] =
      serviceInstance(key)
  }

  final case class Find[T] private[pekko] (key: ServiceKey[T], replyTo: ActorRef[Receptionist.Listing]) extends Command

  final case class Listing[T] private[pekko] (
      key: ServiceKey[T],
      _serviceInstances: Set[ActorRef[T]],
      _allServiceInstances: Set[ActorRef[T]],
      servicesWereAddedOrRemoved: Boolean)
      extends Receptionist.Listing {

    def isForKey(key: ServiceKey[_]): Boolean = key == this.key

    def serviceInstances[M](key: ServiceKey[M]): Set[ActorRef[M]] = {
      if (key != this.key)
        throw new IllegalArgumentException(s"Wrong key [$key] used, must use listing key [${this.key}]")
      _serviceInstances.asInstanceOf[Set[ActorRef[M]]]
    }

    def getServiceInstances[M](key: ServiceKey[M]): java.util.Set[ActorRef[M]] =
      serviceInstances(key).asJava

    override def allServiceInstances[M](key: ServiceKey[M]): Set[ActorRef[M]] = {
      if (key != this.key)
        throw new IllegalArgumentException(s"Wrong key [$key] used, must use listing key [${this.key}]")
      _allServiceInstances.asInstanceOf[Set[ActorRef[M]]]
    }

    override def getAllServiceInstances[M](key: ServiceKey[M]): java.util.Set[ActorRef[M]] =
      allServiceInstances(key).asJava
  }

  final case class Subscribe[T] private[pekko] (key: ServiceKey[T], subscriber: ActorRef[Receptionist.Listing])
      extends Command

}
