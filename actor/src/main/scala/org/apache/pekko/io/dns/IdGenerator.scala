/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.io.dns

import org.apache.pekko.annotation.InternalApi

import java.security.SecureRandom
import java.util.Random
import java.util.concurrent.ThreadLocalRandom

/**
 * INTERNAL API
 *
 * These are called by an actor, however they are called inside composed futures so need to be
 * nextId needs to be thread safe.
 */
@InternalApi
private[pekko] trait IdGenerator {
  def nextId(): Short
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object IdGenerator {
  // Random.nextInt(bound) generates a random int in the range 0 (inclusive) to bound (exclusive),
  // so add 1 to Max Unsigned Short (65535)
  private val UnsignedShortBound = 65536

  sealed trait Policy

  object Policy {
    case object ThreadLocalRandom extends Policy
    case object SecureRandom extends Policy
    case object EnhancedDoubleHashRandom extends Policy
  }

  def apply(policy: Policy): IdGenerator = policy match {
    case Policy.ThreadLocalRandom        => random(ThreadLocalRandom.current())
    case Policy.SecureRandom             => random(new SecureRandom())
    case Policy.EnhancedDoubleHashRandom => new EnhancedDoubleHashGenerator(new SecureRandom())
  }

  def apply(): IdGenerator = random(ThreadLocalRandom.current())

  /**
   * @return a random sequence of ids for production
   */
  def random(rand: java.util.Random): IdGenerator =
    () => (rand.nextInt(UnsignedShortBound) + Short.MinValue).toShort

  private[pekko] class EnhancedDoubleHashGenerator(seed: Random) extends IdGenerator {

    /**
     * An efficient thread safe generator of pseudo random shorts based on
     * https://en.wikipedia.org/wiki/Double_hashing#Enhanced_double_hashing.
     *
     * Note that due to the usage of synchronized this method is optimized
     * for the happy case (i.e. least contention) on multiple threads.
     */
    private var index = seed.nextLong
    private var increment = seed.nextLong
    private var count = 1L

    override final def nextId(): Short = synchronized {
      val result = (0xFFFFFFFF & index).asInstanceOf[Short]
      index -= increment

      // Incorporate the counter into the increment to create a
      // tetrahedral number additional term.
      increment -= {
        count += 1
        count - 1
      }
      result
    }
  }
}
