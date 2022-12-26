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

  // See Scala 2.13 ScalaBatchable for explanation
  def isBatchable(runnable: Runnable): Boolean = runnable match {
    case b: pekko.dispatch.Batchable   => b.isBatchable
    case _: scala.concurrent.Batchable => true
    case _                             => false
  }

}
