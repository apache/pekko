/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.serialization

import java.util.concurrent.CompletionStage

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.event.Logging

/**
 * Serializer that supports async serialization.
 *
 * Only used for Akka persistence journals that explicitly support async serializers.
 *
 * Implementations should typically extend [[AsyncSerializerWithStringManifest]] or
 * [[AsyncSerializerWithStringManifestCS]] that delegates synchronous calls to their async equivalents.
 */
trait AsyncSerializer {

  /**
   * Serializes the given object into an Array of Byte.
   *
   * Note that the array must not be mutated by the serializer after it has been used to complete the returned `Future`.
   */
  def toBinaryAsync(o: AnyRef): Future[Array[Byte]]

  /**
   * Produces an object from an array of bytes, with an optional type-hint.
   */
  def fromBinaryAsync(bytes: Array[Byte], manifest: String): Future[AnyRef]
}

/**
 * Scala API: Async serializer with string manifest that delegates synchronous calls to the asynchronous calls
 * and blocks.
 */
abstract class AsyncSerializerWithStringManifest(system: ExtendedActorSystem)
    extends SerializerWithStringManifest
    with AsyncSerializer {

  private val log = Logging(system, classOf[AsyncSerializerWithStringManifest])

  final override def toBinary(o: AnyRef): Array[Byte] = {
    log.warning(
      "Async serializer called synchronously. This will block. Async serializers should only be used for pekko persistence plugins that support them. Class: {}",
      o.getClass)
    Await.result(toBinaryAsync(o), Duration.Inf)
  }

  final override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    log.warning(
      "Async serializer called synchronously. This will block. Async serializers should only be used for akka persistence plugins that support them. Manifest: [{}]",
      manifest)
    Await.result(fromBinaryAsync(bytes, manifest), Duration.Inf)
  }
}

/**
 * Java API: Async serializer with string manifest that delegates synchronous calls to the asynchronous calls
 * and blocks.
 */
abstract class AsyncSerializerWithStringManifestCS(system: ExtendedActorSystem)
    extends AsyncSerializerWithStringManifest(system) {
  import scala.compat.java8.FutureConverters._

  def toBinaryAsyncCS(o: AnyRef): CompletionStage[Array[Byte]]

  def fromBinaryAsyncCS(bytes: Array[Byte], manifest: String): CompletionStage[AnyRef]

  /**
   * Delegates to [[AsyncSerializerWithStringManifestCS#toBinaryAsyncCS]]
   */
  final def toBinaryAsync(o: AnyRef): Future[Array[Byte]] =
    toBinaryAsyncCS(o).toScala

  /**
   * Delegates to [[AsyncSerializerWithStringManifestCS#fromBinaryAsyncCS]]
   */
  def fromBinaryAsync(bytes: Array[Byte], manifest: String): Future[AnyRef] =
    fromBinaryAsyncCS(bytes, manifest).toScala
}
