/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package org.apache.pekko.util

import org.apache.pekko.util.OptionConverters._

import java.util._

/**
 * These tests are here to ensure that methods from [[org.apache.pekko.util.FutureConverters]], [[org.apache.pekko.util.OptionConverters]]
 * and [[org.apache.pekko.util.FunctionConverters]] that used within Pekko ecosystem but not Pekko core properly cross compile on Scala 2.12
 * and Scala 2.13+.
 *
 * Remove this once Scala 2.12 support is dropped since all methods are in Scala 2.13+ stdlib
 */
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
}
