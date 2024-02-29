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

import org.apache.pekko
import pekko.util.ccompat._
import pekko.util.OptionConverters._

import java.util._
import scala.annotation.nowarn

/**
 * These tests are here to ensure that methods from [[org.apache.pekko.util.FutureConverters]], [[org.apache.pekko.util.OptionConverters]]
 * and [[org.apache.pekko.util.FunctionConverters]] that used within Pekko ecosystem but not Pekko core properly cross compile on Scala 2.12
 * and Scala 2.13+.
 *
 * Remove this once Scala 2.12 support is dropped since all methods are in Scala 2.13+ stdlib
 */

@ccompatUsedUntil213
@nowarn("msg=deprecated")
object Scala212CompatTest {

  // .toJavaPrimitive tests
  val javaDoubleOptional: java.util.Optional[Double] = java.util.Optional.of(1.0)
  val scalaDoubleOption: Option[Double] = Some(1.0)
  val doubleOptionalToJavaPrimitive: OptionalDouble = javaDoubleOptional.toJavaPrimitive
  val doubleOptionToJavaPrimitive: OptionalDouble = scalaDoubleOption.toJavaPrimitive

  val javaIntOptional: java.util.Optional[Int] = java.util.Optional.of(1)
  val scalaIntOption: Option[Int] = Some(1)
  val intOptionalToJavaPrimitive: OptionalInt = javaIntOptional.toJavaPrimitive
  val intOptionToJavaPrimitive: OptionalInt = scalaIntOption.toJavaPrimitive

  val javaLongOptional: java.util.Optional[Long] = java.util.Optional.of(1L)
  val scalaLongOption: Option[Long] = Some(1L)
  val longOptionalToJavaPrimitive: OptionalLong = javaLongOptional.toJavaPrimitive
  val longOptionToJavaPrimitive: OptionalLong = scalaLongOption.toJavaPrimitive

  // from java optional primitive
  val javaOptionalDouble: java.util.OptionalDouble = java.util.OptionalDouble.of(1.0)
  val optionalDoubleToScala: Option[Double] = javaOptionalDouble.toScala
  val optionalDoubleToJavaGeneric: Optional[Double] = javaOptionalDouble.toJavaGeneric

  val javaOptionalInt: java.util.OptionalInt = java.util.OptionalInt.of(1)
  val optionalIntToScala: Option[Int] = javaOptionalInt.toScala
  val optionalIntToJavaGeneric: Optional[Int] = javaOptionalInt.toJavaGeneric

  val javaOptionalLong: java.util.OptionalLong = java.util.OptionalLong.of(1L)
  val optionalLongToScala: Option[Long] = javaOptionalLong.toScala
  val optionalLongToJavaGeneric: Optional[Long] = javaOptionalLong.toJavaGeneric

  // OptionConverters toScala and toJava
  OptionConverters.toJava(OptionConverters.toScala(java.util.Optional.of("")))
  OptionConverters.toJava(OptionConverters.toScala(java.util.OptionalDouble.of(1.0)))
  OptionConverters.toJava(OptionConverters.toScala(java.util.OptionalInt.of(1)))
  OptionConverters.toJava(OptionConverters.toScala(java.util.OptionalLong.of(1L)))

  // Iterable.single
  val queue = scala.collection.immutable.Queue.empty[ByteString]
  queue.enqueue(Iterable.single(ByteString.empty))

}
