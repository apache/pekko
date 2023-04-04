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

import java.util.Optional

/**
 * INTERNAL API
 *
 * Remove this once Scala 2.12 support is dropped since all methods are in Scala 2.13+ stdlib
 */
@InternalStableApi
private[pekko] object OptionConverters {
  implicit final class RichOptional[A](private val o: java.util.Optional[A]) extends AnyVal {
    @inline def toScala: Option[A] = scala.compat.java8.OptionConverters.RichOptionalGeneric(o).asScala
  }

  implicit final class RichOption[A](private val o: Option[A]) extends AnyVal {
    @inline def toJava: Optional[A] = scala.compat.java8.OptionConverters.RichOptionForJava8(o).asJava
  }
}
