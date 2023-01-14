/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.annotation;

import java.lang.annotation.*;

/**
 * Marks APIs that are considered internal to Akka and may change at any point in time without any
 * warning.
 *
 * <p>For example, this annotation should be used when the Scala {@code private[pekko]} access
 * restriction is used, as Java has no way of representing this package restricted access and such
 * methods and classes are represented as {@code public} in byte-code.
 *
 * <p>If a method/class annotated with this method has a javadoc/scaladoc comment, the first line
 * MUST include {@code INTERNAL API} in order to be easily identifiable from generated
 * documentation. Additional information may be put on the same line as the INTERNAL API comment in
 * order to clarify further.
 */
@Documented
@Retention(RetentionPolicy.CLASS) // to be accessible by MiMa
@Target({
  ElementType.METHOD,
  ElementType.CONSTRUCTOR,
  ElementType.FIELD,
  ElementType.TYPE,
  ElementType.PACKAGE
})
public @interface InternalApi {}
