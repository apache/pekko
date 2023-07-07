/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
  @inline final def asJava[T](f: Future[T]): CompletionStage[T] = scala.compat.java8.FutureConverters.toJava(f)

  implicit final class FutureOps[T](private val f: Future[T]) extends AnyVal {
    @inline def asJava: CompletionStage[T] = FutureConverters.asJava(f)
  }

  @inline final def asScala[T](cs: CompletionStage[T]): Future[T] = scala.compat.java8.FutureConverters.toScala(cs)

  implicit final class CompletionStageOps[T](private val cs: CompletionStage[T]) extends AnyVal {
    @inline def asScala: Future[T] = FutureConverters.asScala(cs)
  }
}
