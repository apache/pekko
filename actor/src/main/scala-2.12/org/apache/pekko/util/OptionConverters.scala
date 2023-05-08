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

import java.util._

/**
 * INTERNAL API
 *
 * Remove this once Scala 2.12 support is dropped since all methods are in Scala 2.13+ stdlib
 */
@InternalStableApi
private[pekko] object OptionConverters {
  import scala.compat.java8.OptionConverters.SpecializerOfOptions

  @inline final def toScala[A](o: Optional[A]): Option[A] = scala.compat.java8.OptionConverters.toScala(o)

  @inline final def toJava[A](o: Option[A]): Optional[A] = scala.compat.java8.OptionConverters.toJava(o)

  implicit final class RichOptional[A](private val o: java.util.Optional[A]) extends AnyVal {
    @inline def toScala: Option[A] = scala.compat.java8.OptionConverters.RichOptionalGeneric(o).asScala

    @inline def toJavaPrimitive[O](implicit specOp: SpecializerOfOptions[A, O]): O =
      scala.compat.java8.OptionConverters.RichOptionalGeneric(o).asPrimitive
  }

  implicit final class RichOption[A](private val o: Option[A]) extends AnyVal {
    @inline def toJava: Optional[A] = scala.compat.java8.OptionConverters.RichOptionForJava8(o).asJava

    @inline def toJavaPrimitive[O](implicit specOp: SpecializerOfOptions[A, O]): O =
      scala.compat.java8.OptionConverters.RichOptionForJava8(o).asPrimitive
  }

  implicit class RichOptionalDouble(private val o: OptionalDouble) extends AnyVal {

    /** Convert a Java `OptionalDouble` to a Scala `Option` */
    @inline def toScala: Option[Double] = scala.compat.java8.OptionConverters.RichOptionalDouble(o).asScala

    /** Convert a Java `OptionalDouble` to a generic Java `Optional` */
    @inline def toJavaGeneric: Optional[Double] = scala.compat.java8.OptionConverters.RichOptionalDouble(o).asGeneric
  }

  /** Provides conversions from `OptionalInt` to Scala `Option` and the generic `Optional` */
  implicit class RichOptionalInt(private val o: OptionalInt) extends AnyVal {

    /** Convert a Java `OptionalInt` to a Scala `Option` */
    @inline def toScala: Option[Int] = scala.compat.java8.OptionConverters.RichOptionalInt(o).asScala

    /** Convert a Java `OptionalInt` to a generic Java `Optional` */
    @inline def toJavaGeneric: Optional[Int] = scala.compat.java8.OptionConverters.RichOptionalInt(o).asGeneric
  }

  /** Provides conversions from `OptionalLong` to Scala `Option` and the generic `Optional` */
  implicit class RichOptionalLong(private val o: OptionalLong) extends AnyVal {

    /** Convert a Java `OptionalLong` to a Scala `Option` */
    @inline def toScala: Option[Long] = scala.compat.java8.OptionConverters.RichOptionalLong(o).asScala

    /** Convert a Java `OptionalLong` to a generic Java `Optional` */
    @inline def toJavaGeneric: Optional[Long] = scala.compat.java8.OptionConverters.RichOptionalLong(o).asGeneric
  }
}
