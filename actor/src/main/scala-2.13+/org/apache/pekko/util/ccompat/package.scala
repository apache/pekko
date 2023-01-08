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

package org.apache.pekko.util

/**
 * INTERNAL API
 *
 * Based on https://github.com/scala/scala-collection-compat/blob/master/compat/src/main/scala-2.13/scala/collection/compat/package.scala
 * but reproduced here so we don't need to add a dependency on this library. It contains much more than we need right now, and is
 * not promising binary compatibility yet at the time of writing.
 */
package object ccompat {
  private[pekko] type Factory[-A, +C] = scala.collection.Factory[A, C]
  private[pekko] val Factory = scala.collection.Factory

  // When we drop support for 2.12 we can delete this concept
  // and import scala.jdk.CollectionConverters.Ops._ instead
  object JavaConverters
      extends scala.collection.convert.AsJavaExtensions
      with scala.collection.convert.AsScalaExtensions
}
