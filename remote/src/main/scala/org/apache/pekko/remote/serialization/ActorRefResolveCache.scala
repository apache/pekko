/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.serialization

import scala.reflect.ClassTag

import org.apache.pekko
import pekko.actor.{
  ActorRef,
  ActorSystem,
  ClassicActorSystemProvider,
  EmptyLocalActorRef,
  ExtendedActorSystem,
  Extension,
  ExtensionId,
  ExtensionIdProvider,
  InternalActorRef
}
import pekko.remote.{ RemoteActorRef, RemoteActorRefProvider }
import pekko.remote.artery.LruBoundedCache
import pekko.util.{ unused, Unsafe }

/**
 * INTERNAL API: Thread local cache per actor system
 */
private[pekko] object ActorRefResolveThreadLocalCache
    extends ExtensionId[ActorRefResolveThreadLocalCache]
    with ExtensionIdProvider {

  override def get(system: ActorSystem): ActorRefResolveThreadLocalCache = super.get(system)
  override def get(system: ClassicActorSystemProvider): ActorRefResolveThreadLocalCache = super.get(system)

  override def lookup = ActorRefResolveThreadLocalCache

  override def createExtension(system: ExtendedActorSystem): ActorRefResolveThreadLocalCache =
    new ActorRefResolveThreadLocalCache(system)
}

/**
 * INTERNAL API
 */
private[pekko] class ActorRefResolveThreadLocalCache(val system: ExtendedActorSystem) extends Extension {

  private val provider = system.provider match {
    case r: RemoteActorRefProvider => r
    case _                         =>
      throw new IllegalArgumentException(
        "ActorRefResolveThreadLocalCache can only be used with RemoteActorRefProvider, " +
        s"not with ${system.provider.getClass}")
  }

  private val current = new ThreadLocal[ActorRefResolveCache] {
    override def initialValue: ActorRefResolveCache = new ActorRefResolveCache(provider)
  }

  def threadLocalCache(@unused provider: RemoteActorRefProvider): ActorRefResolveCache =
    current.get

}

/**
 * INTERNAL API
 */
private[pekko] final class ActorRefResolveCache(provider: RemoteActorRefProvider)
    extends AbstractActorRefResolveCache[ActorRef] {

  override protected def compute(k: String): ActorRef =
    provider.internalResolveActorRef(k)
}

/**
 * INTERNAL API
 */
private[pekko] abstract class AbstractActorRefResolveCache[R <: ActorRef: ClassTag]
    extends LruBoundedCache[String, R](capacity = 1024, evictAgeThreshold = 600) {

  /**
   * Compared to `getOrCompute` this will also invalidate cachedAssociation of RemoteActorRef
   * if the `Association` is removed.
   */
  def resolve(k: String): R = {
    val ref = getOrCompute(k)
    ref match {
      case r: RemoteActorRef =>
        val cachedAssociation = r.cachedAssociation
        if ((cachedAssociation ne null) && cachedAssociation.isRemovedAfterQuarantined())
          r.cachedAssociation = null
      case _ =>
    }
    ref
  }

  override protected def compute(k: String): R

  override protected def hash(k: String): Int = Unsafe.fastHash(k)

  override protected def isKeyCacheable(k: String): Boolean = true
  override protected def isCacheable(ref: R): Boolean =
    ref match {
      case _: EmptyLocalActorRef => false
      case _                     =>
        // "temp" only for one request-response interaction so don't cache
        !InternalActorRef.isTemporaryRef(ref)
    }
}
