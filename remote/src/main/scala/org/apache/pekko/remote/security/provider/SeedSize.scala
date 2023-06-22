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

package org.apache.pekko.remote.security.provider

/**
 * INTERNAL API
 * From AESCounterRNG API docs:
 * Valid values are 16 (128 bits), 24 (192 bits) and 32 (256 bits).
 * Any other values will result in an exception from the AES implementation.
 *
 * INTERNAL API
 */
private[provider] object SeedSize {
  val Seed128 = 16
  val Seed192 = 24
  val Seed256 = 32
}
