/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.io.dns

import org.apache.pekko.testkit.PekkoSpec

class IdGeneratorSpec extends PekkoSpec {

  "IdGenerator" must {
    "provide a thread-local-random" in {
      val gen = IdGenerator(IdGenerator.Policy.ThreadLocalRandom)
      gen.nextId() should be <= Short.MaxValue
      gen.nextId() should be >= Short.MinValue
    }

    "provide a secure-random" in {
      val gen = IdGenerator(IdGenerator.Policy.SecureRandom)
      gen.nextId() should be <= Short.MaxValue
      gen.nextId() should be >= Short.MinValue
    }
  }
}
