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

package org.apache.pekko.actor.testkit.typed.javadsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl
import pekko.actor.typed.ActorSystem

/**
 * Utilities to test serialization.
 */
class SerializationTestKit(system: ActorSystem[_]) {

  private val delegate = new scaladsl.SerializationTestKit(system)

  /**
   * Verify serialization roundtrip.
   * Throws exception from serializer if `obj` can't be serialized and deserialized.
   *
   * @param obj the object to verify
   * @param assertEquality if `true` the deserialized  object is verified to be equal to `obj`,
   *                       and if not an `AssertionError` is thrown
   * @return the deserialized object
   */
  def verifySerialization[M](obj: M, assertEquality: Boolean): M =
    delegate.verifySerialization(obj, assertEquality)
}
