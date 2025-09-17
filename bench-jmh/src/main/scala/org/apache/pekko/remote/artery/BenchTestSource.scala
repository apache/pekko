/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.stream.stage.{ GraphStage, GraphStageLogic, OutHandler }
import pekko.stream.{ Attributes, Outlet, SourceShape }

/**
 * Emits integers from 1 to the given `elementCount`. The `java.lang.Integer`
 * objects are allocated in the constructor of the operator, so it should be created
 * before the benchmark is started.
 */
class BenchTestSource(elementCount: Int) extends GraphStage[SourceShape[java.lang.Integer]] {

  private val elements = new Array[java.lang.Integer](elementCount)
  (1 to elementCount).foreach(n => elements(n - 1) = n)

  val out: Outlet[java.lang.Integer] = Outlet("BenchTestSource")
  override val shape: SourceShape[java.lang.Integer] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {

      var n = 0

      override def onPull(): Unit = {
        n += 1
        if (n > elementCount)
          complete(out)
        else
          push(out, elements(n - 1))
      }

      setHandler(out, this)
    }
}

class BenchTestSourceSameElement[T](elements: Int, elem: T) extends GraphStage[SourceShape[T]] {

  val out: Outlet[T] = Outlet("BenchTestSourceSameElement")
  override val shape: SourceShape[T] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {

      var n = 0

      override def onPull(): Unit = {
        n += 1
        if (n > elements)
          complete(out)
        else
          push(out, elem)
      }

      setHandler(out, this)
    }
}
