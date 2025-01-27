/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.stage.{ GraphStage, GraphStageLogic, OutHandler }

import java.util.Spliterator
import java.util.function.Consumer

/** INTERNAL API */
@InternalApi private[stream] final class JavaStreamSource[T, S <: java.util.stream.BaseStream[T, S]](
    val open: () => java.util.stream.BaseStream[T, S])
    extends GraphStage[SourceShape[T]] {

  val out: Outlet[T] = Outlet("JavaStreamSource")
  override val shape: SourceShape[T] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler with Consumer[T] {
      private var stream: java.util.stream.BaseStream[T, S] = _
      private var splitIterator: Spliterator[T] = _

      final override def preStart(): Unit = {
        stream = open()
        splitIterator = stream.spliterator()
        if (splitIterator.hasCharacteristics(Spliterator.SIZED) && splitIterator.estimateSize() == 0) {
          complete(out)
        }
      }

      final override def accept(elem: T): Unit = push(out, elem)

      final override def postStop(): Unit =
        if (stream ne null) {
          stream.close()
        }

      final override def onPull(): Unit = if (!splitIterator.tryAdvance(this)) {
        complete(out)
      }

      setHandler(out, this)
    }

  override def toString: String = "JavaStreamSource"
}
