/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage._

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object EmptySource extends GraphStage[SourceShape[Nothing]] {
  val out = Outlet[Nothing]("EmptySource.out")
  override val shape = SourceShape(out)

  override protected def initialAttributes = DefaultAttributes.emptySource

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {
      override def preStart(): Unit = completeStage()
      override def onPull(): Unit = completeStage()

      setHandler(out, this)
    }

  override def toString = "EmptySource"
}
