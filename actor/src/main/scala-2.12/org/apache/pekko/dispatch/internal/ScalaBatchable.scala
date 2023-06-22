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

  // see Scala 2.13 source tree for explanation
  def isBatchable(runnable: Runnable): Boolean = runnable match {
    case b: pekko.dispatch.Batchable            => b.isBatchable
    case _: scala.concurrent.OnCompleteRunnable => true
    case _                                      => false
  }

}
