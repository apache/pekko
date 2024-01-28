/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package org.apache.pekko.util

import org.apache.pekko.annotation.InternalStableApi

import java.util.concurrent.CompletionStage
import scala.concurrent.Future

/**
 * INTERNAL API
 *
 * Remove this once Scala 2.12 support is dropped since all methods are in Scala 2.13+ stdlib
 */
@InternalStableApi
private[pekko] object FutureConverters {
  import scala.jdk.javaapi

  // Ideally this should have the Scala 3 inline keyword but then Java sources are
  // unable to call this method, see https://github.com/lampepfl/dotty/issues/19346
  def asJava[T](f: Future[T]): CompletionStage[T] = javaapi.FutureConverters.asJava(f)

  implicit final class FutureOps[T](private val f: Future[T]) extends AnyVal {
    def asJava: CompletionStage[T] = javaapi.FutureConverters.asJava(f)
  }

  // Ideally this should have the Scala 3 inline keyword but then Java sources are
  // unable to call this method, see https://github.com/lampepfl/dotty/issues/19346
  def asScala[T](cs: CompletionStage[T]): Future[T] = javaapi.FutureConverters.asScala(cs)

  implicit final class CompletionStageOps[T](private val cs: CompletionStage[T]) extends AnyVal {
    def asScala: Future[T] = javaapi.FutureConverters.asScala(cs)
  }
}
