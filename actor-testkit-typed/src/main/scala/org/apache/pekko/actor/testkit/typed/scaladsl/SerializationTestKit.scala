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

package org.apache.pekko.actor.testkit.typed.scaladsl

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.adapter._
import pekko.serialization.SerializationExtension
import pekko.serialization.Serializers

/**
 * Utilities to test serialization.
 */
class SerializationTestKit(system: ActorSystem[_]) {

  private val serialization = SerializationExtension(system.toClassic)

  /**
   * Verify serialization roundtrip.
   * Throws exception from serializer if `obj` can't be serialized and deserialized.
   * Also tests that the deserialized  object is equal to `obj`, and if not an
   * `AssertionError` is thrown.
   *
   * @param obj the object to verify
   * @return the deserialized object
   */
  def verifySerialization[M](obj: M): M =
    verifySerialization(obj, assertEquality = true)

  /**
   * Verify serialization roundtrip.
   * Throws exception from serializer if `obj` can't be serialized and deserialized.
   *
   * @param obj the object to verify
   * @param assertEquality if `true` the deserialized  object is verified to be equal to `obj`,
   *                       and if not an `AssertionError` is thrown
   * @return the deserialized object
   */
  def verifySerialization[M](obj: M, assertEquality: Boolean): M = {
    val result = roundtrip(obj)
    if (assertEquality && result != obj)
      throw new AssertionError(s"Serialization verification expected $obj, but was $result")
    result
  }

  /**
   * Verify serialization roundtrip.
   * Throws exception from serializer if `obj` can't be serialized and deserialized.
   *
   * @return the deserialized object
   */
  private def roundtrip[M](obj: M): M = {
    val objAnyRef = obj.asInstanceOf[AnyRef]
    val bytes = serialization.serialize(objAnyRef).get
    val serializer = serialization.findSerializerFor(objAnyRef)
    val manifest = Serializers.manifestFor(serializer, objAnyRef)
    serialization.deserialize(bytes, serializer.identifier, manifest).get.asInstanceOf[M]
  }
}
