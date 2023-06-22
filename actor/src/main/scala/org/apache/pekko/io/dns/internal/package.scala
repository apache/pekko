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

package org.apache.pekko.io.dns

import java.nio.ByteOrder

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.io.dns.CachePolicy.{ CachePolicy, Forever, Never, Ttl }

/**
 * INTERNAL API
 */
package object internal {

  /**
   * INTERNAL API
   *
   * We know we always want to use network byte order when writing
   */
  @InternalApi
  private[pekko] implicit val networkByteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

  @InternalApi
  private[pekko] implicit object CachePolicyIsOrdered extends Ordering[CachePolicy] {
    def compare(a: CachePolicy, b: CachePolicy): Int =
      (a, b) match {
        case (Forever, Forever) => 0
        case (Never, Never)     => 0
        case (v1: Ttl, v2: Ttl) => v1.value.compare(v2.value)
        case (Never, _)         => -1
        case (_, Forever)       => -1
        case (Forever, _)       => 1
        case (_, Never)         => 1
      }
  }

}
