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

package org.apache.pekko.dispatch.internal

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object ScalaBatchable {

  /*
   * In Scala 2.13.0 `OnCompleteRunnable` was deprecated, and in 2.13.4
   * `impl.Promise.Transformation` no longer extend the deprecated `OnCompleteRunnable`
   * but instead `scala.concurrent.Batchable` (which anyway extends `OnCompleteRunnable`).
   * On top of that we have or own legacy version `org.apache.pekko.dispatch.Batchable`.
   */

  def isBatchable(runnable: Runnable): Boolean = runnable match {
    case b: pekko.dispatch.Batchable   => b.isBatchable
    case _: scala.concurrent.Batchable => true
    case _                             => false
  }

}
