/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec
import scala.collection.immutable

import org.apache.pekko
import pekko.actor.NoSerializationVerificationNeeded
import pekko.annotation.InternalApi
import pekko.io.dns.CachePolicy.CachePolicy
import pekko.io.dns.CachePolicy.Forever
import pekko.io.dns.CachePolicy.Never
import pekko.io.dns.CachePolicy.Ttl
import pekko.io.dns.DnsProtocol
import pekko.io.dns.DnsProtocol.{ RequestType, Resolved }

private[io] trait PeriodicCacheCleanup {
  def cleanup(): Unit
}

class SimpleDnsCache extends Dns with PeriodicCacheCleanup with NoSerializationVerificationNeeded {
  import SimpleDnsCache._
  private val cacheRef = new AtomicReference(
    new Cache[(String, RequestType), Resolved](
      immutable.SortedSet()(expiryEntryOrdering()),
      immutable.Map(),
      () => clock()))

  private val nanoBase = System.nanoTime()

  override def cached(request: DnsProtocol.Resolve): Option[DnsProtocol.Resolved] =
    cacheRef.get().get((request.name, request.requestType))

  // Milliseconds since start
  protected def clock(): Long = {
    val now = System.nanoTime()
    if (now - nanoBase < 0) 0
    else (now - nanoBase) / 1000000
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] final def get(key: (String, RequestType)): Option[Resolved] = {
    cacheRef.get().get(key)
  }

  @tailrec
  private[io] final def put(key: (String, RequestType), records: Resolved, ttl: CachePolicy): Unit = {
    val c = cacheRef.get()
    if (!cacheRef.compareAndSet(c, c.put(key, records, ttl)))
      put(key, records, ttl)
  }

  @tailrec
  override final def cleanup(): Unit = {
    val c = cacheRef.get()
    if (!cacheRef.compareAndSet(c, c.cleanup()))
      cleanup()
  }

}
object SimpleDnsCache {

  /**
   * INTERNAL API
   */
  @InternalApi
  private[io] class Cache[K, V](
      queue: immutable.SortedSet[ExpiryEntry[K]],
      cache: immutable.Map[K, CacheEntry[V]],
      clock: () => Long) {
    def get(name: K): Option[V] = {
      for {
        e <- cache.get(name)
        if e.isValid(clock())
      } yield e.answer
    }

    def put(name: K, answer: V, ttl: CachePolicy): Cache[K, V] = {
      val until = ttl match {
        case Forever  => Long.MaxValue
        case Never    => clock() - 1
        case ttl: Ttl => clock() + ttl.value.toMillis
      }

      new Cache[K, V](queue + new ExpiryEntry[K](name, until), cache + (name -> CacheEntry(answer, until)), clock)
    }

    def cleanup(): Cache[K, V] = {
      val now = clock()
      var q = queue
      var c = cache
      while (q.nonEmpty && !q.head.isValid(now)) {
        val minEntry = q.head
        val name = minEntry.name
        q -= minEntry
        if (c.get(name).filterNot(_.isValid(now)).isDefined)
          c -= name
      }
      new Cache(q, c, clock)
    }
  }

  private[io] case class CacheEntry[T](answer: T, until: Long) {
    def isValid(clock: Long): Boolean = clock < until
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[io] class ExpiryEntry[K](val name: K, val until: Long) extends Ordered[ExpiryEntry[K]] {
    def isValid(clock: Long): Boolean = clock < until
    override def compare(that: ExpiryEntry[K]): Int = -until.compareTo(that.until)
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[io] def expiryEntryOrdering[K]() = new Ordering[ExpiryEntry[K]] {
    override def compare(x: ExpiryEntry[K], y: ExpiryEntry[K]): Int = {
      x.until.compareTo(y.until)
    }
  }
}
