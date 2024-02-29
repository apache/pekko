/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.compat

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Compatibility wrapper for `scala.PartialFunction` to be able to compile the same code
 * against Scala 2.12, 2.13, 3.0
 *
 * Remove these classes as soon as support for Scala 2.12 is dropped!
 */
@InternalApi private[pekko] object PartialFunction {

  def fromFunction[A, B](f: A => B): scala.PartialFunction[A, B] =
    scala.PartialFunction.fromFunction(f)

}
