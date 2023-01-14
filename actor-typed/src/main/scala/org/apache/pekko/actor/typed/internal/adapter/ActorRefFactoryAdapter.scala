/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal.adapter

import org.apache.pekko
import pekko.ConfigurationException
import pekko.actor.typed._
import pekko.annotation.InternalApi
import pekko.util.ErrorMessages

/**
 * INTERNAL API
 */
@InternalApi private[typed] object ActorRefFactoryAdapter {

  private val remoteDeploymentNotAllowed = "Remote deployment not allowed for typed actors"
  def spawnAnonymous[T](
      context: pekko.actor.ActorRefFactory,
      behavior: Behavior[T],
      props: Props,
      rethrowTypedFailure: Boolean): ActorRef[T] = {
    try {
      ActorRefAdapter(context.actorOf(internal.adapter.PropsAdapter(() => behavior, props, rethrowTypedFailure)))
    } catch {
      case ex: ConfigurationException if ex.getMessage.startsWith(ErrorMessages.RemoteDeploymentConfigErrorPrefix) =>
        throw new ConfigurationException(remoteDeploymentNotAllowed, ex)
    }
  }

  def spawn[T](
      actorRefFactory: pekko.actor.ActorRefFactory,
      behavior: Behavior[T],
      name: String,
      props: Props,
      rethrowTypedFailure: Boolean): ActorRef[T] = {
    try {
      ActorRefAdapter(
        actorRefFactory.actorOf(
          internal.adapter.PropsAdapter(() => Behavior.validateAsInitial(behavior), props, rethrowTypedFailure),
          name))
    } catch {
      case ex: ConfigurationException if ex.getMessage.startsWith(ErrorMessages.RemoteDeploymentConfigErrorPrefix) =>
        throw new ConfigurationException(remoteDeploymentNotAllowed, ex)
    }
  }
}
