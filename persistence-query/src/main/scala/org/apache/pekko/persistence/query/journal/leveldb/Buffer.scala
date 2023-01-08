/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.journal.leveldb

import java.util

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.Outlet
import pekko.stream.stage.GraphStageLogic
import pekko.util.ccompat.JavaConverters._

/**
 * INTERNAL API
 */
@InternalApi
private[leveldb] abstract trait Buffer[T] { self: GraphStageLogic =>

  def doPush(out: Outlet[T], elem: T): Unit

  private val buf: java.util.LinkedList[T] = new util.LinkedList[T]()
  def buffer(element: T): Unit = {
    buf.add(element)
  }
  def buffer(all: Set[T]): Unit = {
    buf.addAll(all.asJava)
  }
  def deliverBuf(out: Outlet[T]): Unit = {
    if (!buf.isEmpty && isAvailable(out)) {
      doPush(out, buf.remove())
    }
  }
  def bufferSize: Int = buf.size
  def bufferEmpty: Boolean = buf.isEmpty
}
