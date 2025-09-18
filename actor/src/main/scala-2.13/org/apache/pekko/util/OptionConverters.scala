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

import java.util._

import scala.jdk.OptionShape

import org.apache.pekko.annotation.InternalStableApi

/**
 * INTERNAL API
 *
 * Remove this once Scala 2.12 support is dropped since all methods are in Scala 2.13+ stdlib
 */
@InternalStableApi
private[pekko] object OptionConverters {

  @inline final def toScala[A](o: Optional[A]): Option[A] = scala.jdk.javaapi.OptionConverters.toScala(o)

  @inline def toScala(o: OptionalDouble): Option[java.lang.Double] = scala.jdk.javaapi.OptionConverters.toScala(o)

  @inline def toScala(o: OptionalInt): Option[java.lang.Integer] = scala.jdk.javaapi.OptionConverters.toScala(o)

  @inline def toScala(o: OptionalLong): Option[java.lang.Long] = scala.jdk.javaapi.OptionConverters.toScala(o)

  @inline final def toJava[A](o: Option[A]): Optional[A] = scala.jdk.javaapi.OptionConverters.toJava(o)

  implicit final class RichOptional[A](private val o: java.util.Optional[A]) extends AnyVal {
    @inline def toScala: Option[A] = scala.jdk.OptionConverters.RichOptional(o).toScala

    @inline def toJavaPrimitive[O](implicit shape: OptionShape[A, O]): O =
      scala.jdk.OptionConverters.RichOptional(o).toJavaPrimitive
  }

  implicit final class RichOption[A](private val o: Option[A]) extends AnyVal {
    @inline def toJava: Optional[A] = scala.jdk.OptionConverters.RichOption(o).toJava

    @inline def toJavaPrimitive[O](implicit shape: OptionShape[A, O]): O =
      scala.jdk.OptionConverters.RichOption(o).toJavaPrimitive
  }

  implicit class RichOptionalDouble(private val o: OptionalDouble) extends AnyVal {

    /** Convert a Java `OptionalDouble` to a Scala `Option` */
    @inline def toScala: Option[Double] = scala.jdk.OptionConverters.RichOptionalDouble(o).toScala

    /** Convert a Java `OptionalDouble` to a generic Java `Optional` */
    @inline def toJavaGeneric: Optional[Double] = scala.jdk.OptionConverters.RichOptionalDouble(o).toJavaGeneric
  }

  /** Provides conversions from `OptionalInt` to Scala `Option` and the generic `Optional` */
  implicit class RichOptionalInt(private val o: OptionalInt) extends AnyVal {

    /** Convert a Java `OptionalInt` to a Scala `Option` */
    @inline def toScala: Option[Int] = scala.jdk.OptionConverters.RichOptionalInt(o).toScala

    /** Convert a Java `OptionalInt` to a generic Java `Optional` */
    @inline def toJavaGeneric: Optional[Int] = scala.jdk.OptionConverters.RichOptionalInt(o).toJavaGeneric
  }

  /** Provides conversions from `OptionalLong` to Scala `Option` and the generic `Optional` */
  implicit class RichOptionalLong(private val o: OptionalLong) extends AnyVal {

    /** Convert a Java `OptionalLong` to a Scala `Option` */
    @inline def toScala: Option[Long] = scala.jdk.OptionConverters.RichOptionalLong(o).toScala

    /** Convert a Java `OptionalLong` to a generic Java `Optional` */
    @inline def toJavaGeneric: Optional[Long] = scala.jdk.OptionConverters.RichOptionalLong(o).toJavaGeneric
  }
}
