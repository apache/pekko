/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.reactivestreams.Processor

import org.apache.pekko
import pekko.stream.Attributes
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.scaladsl.Flow
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler

class TransformProcessorTest extends AkkaIdentityProcessorVerification[Int] {

  override def createIdentityProcessor(maxBufferSize: Int): Processor[Int, Int] = {
    val stage =
      new SimpleLinearGraphStage[Int] {
        override def createLogic(inheritedAttributes: Attributes) =
          new GraphStageLogic(shape) with InHandler with OutHandler {
            override def onPush(): Unit = push(out, grab(in))
            override def onPull(): Unit = pull(in)
            setHandlers(in, out, this)
          }
      }

    Flow[Int]
      .via(stage)
      .toProcessor
      .withAttributes(Attributes.inputBuffer(initial = maxBufferSize / 2, max = maxBufferSize))
      .run()
  }

  override def createElement(element: Int): Int = element

}
