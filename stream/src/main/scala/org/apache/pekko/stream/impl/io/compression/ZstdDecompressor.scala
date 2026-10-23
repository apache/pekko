/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.impl.io.compression

import java.nio.ByteBuffer

import scala.util.control.NonFatal

import com.github.luben.zstd.{ Zstd, ZstdDirectBufferDecompressingStreamNoFinalizer }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.io.ByteBufferCleaner
import pekko.stream.Attributes
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.stage.{ GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString

/** INTERNAL API */
@InternalApi private[pekko] class ZstdDecompressor(maxBytesPerChunk: Int =
      Zstd.blockSizeMax()) extends SimpleLinearGraphStage[ByteString] {

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogic(shape) with InHandler with OutHandler {

      private val sourceBuffer = ByteBuffer.allocateDirect(maxBytesPerChunk)

      // This is initialized here to avoid an allocation per onPush, remember to clear before using
      private val outputBuffer = ByteBuffer.allocateDirect(maxBytesPerChunk)
      private val decompressingStream = new ZstdDirectBufferDecompressingStreamNoFinalizer(sourceBuffer)

      override def onPush(): Unit = {
        sourceBuffer.clear()
        val inputArray = grab(in).toArrayUnsafe()
        sourceBuffer.put(inputArray)
        sourceBuffer.flip()
        if (sourceBuffer.hasRemaining) {
          var result = ByteString.empty
          while (sourceBuffer.hasRemaining) {
            outputBuffer.clear()
            decompressingStream.read(outputBuffer)
            outputBuffer.flip()
            val outputArray = new Array[Byte](outputBuffer.limit())
            outputBuffer.get(outputArray)
            result = result.concat(ByteString.fromArrayUnsafe(outputArray))
          }

          // Fencepost case, it's possible to still have sourceBuffer.hasRemaining and yet decompression result
          // not output anything
          if (result.nonEmpty)
            push(out, result)
          else
            pull(in)
        } else pull(in)
        sourceBuffer.flip()
      }

      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit = {
        sourceBuffer.flip()
        if (sourceBuffer.hasRemaining) {
          var result = ByteString.empty
          while (sourceBuffer.hasRemaining) {
            outputBuffer.clear()
            decompressingStream.read(outputBuffer)
            outputBuffer.flip()
            val outputArray = new Array[Byte](outputBuffer.limit())
            outputBuffer.get(outputArray)
            result = result.concat(ByteString.fromArrayUnsafe(outputArray))
          }
          decompressingStream.close()

          // Fencepost case, it's possible to still have sourceBuffer.hasRemaining and yet decompression result
          // not output anything
          if (result.nonEmpty)
            emit(out, result)
        }
        completeStage()
      }

      override def postStop(): Unit = {
        decompressingStream.close()

        if (ByteBufferCleaner.isSupported)
          try {
            ByteBufferCleaner.clean(sourceBuffer)
            ByteBufferCleaner.clean(outputBuffer)
          } catch {
            case NonFatal(_) => /* ok, best effort attempt to cleanup failed */
          }
      }

      setHandlers(in, out, this)
    }
  }

}
