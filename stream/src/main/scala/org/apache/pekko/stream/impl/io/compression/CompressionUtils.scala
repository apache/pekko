/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl.io.compression

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.stream.Attributes
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.scaladsl.Flow
import pekko.stream.stage.{ GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString

/** INTERNAL API */
@InternalApi private[stream] object CompressionUtils {

  /**
   * Creates a flow from a compressor constructor.
   */
  def compressorFlow(newCompressor: () => Compressor): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph {
      new SimpleLinearGraphStage[ByteString] {
        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler with OutHandler {
            val compressor = newCompressor()

            override def onPush(): Unit = {
              val data = compressor.compressAndFlush(grab(in))
              if (data.nonEmpty) push(out, data)
              else pull(in)
            }

            override def onPull(): Unit = pull(in)

            override def onUpstreamFinish(): Unit = {
              val data = compressor.finish()
              if (data.nonEmpty) emit(out, data)
              completeStage()
            }

            override def postStop(): Unit = compressor.close()

            setHandlers(in, out, this)
          }
      }
    }
}
