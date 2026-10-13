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

package org.apache.pekko.actor.typed.internal.adapter

import org.apache.pekko
import pekko.ConfigurationException
import pekko.actor.typed._
import pekko.actor.typed.internal.CachedProps
import pekko.annotation.InternalApi
import pekko.util.ErrorMessages
import pekko.util.OptionVal

/**
 * INTERNAL API
 */
@InternalApi private[typed] object ActorRefFactoryAdapter {

  private val remoteDeploymentNotAllowed = "Remote deployment not allowed for typed actors"

  private def classicPropsFor[T](behavior: Behavior[T], props: Props, rethrowTypedFailure: Boolean): pekko.actor.Props =
    behavior._internalClassicPropsCache match {
      case OptionVal.Some(cachedProps)
          if (cachedProps.typedProps eq props) && cachedProps.rethrowTypedFailure == rethrowTypedFailure =>
        cachedProps.adaptedProps
      case _ =>
        val adapted =
          internal.adapter.PropsAdapter(() => Behavior.validateAsInitial(behavior), props, rethrowTypedFailure)
        // we only optimistically cache the last seen typed props instance, since for most scenarios
        // with large numbers of actors, they will be of the same type and the same props
        behavior._internalClassicPropsCache = OptionVal.Some(CachedProps(props, adapted, rethrowTypedFailure))
        adapted
    }

  def spawnAnonymous[T](
      context: pekko.actor.ActorRefFactory,
      behavior: Behavior[T],
      props: Props,
      rethrowTypedFailure: Boolean): ActorRef[T] = {
    try {
      ActorRefAdapter(context.actorOf(classicPropsFor(behavior, props, rethrowTypedFailure)))
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
      ActorRefAdapter(actorRefFactory.actorOf(classicPropsFor(behavior, props, rethrowTypedFailure), name))
    } catch {
      case ex: ConfigurationException if ex.getMessage.startsWith(ErrorMessages.RemoteDeploymentConfigErrorPrefix) =>
        throw new ConfigurationException(remoteDeploymentNotAllowed, ex)
    }
  }
}
