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

import scala.annotation.tailrec
import scala.util.control.NonFatal

import com.github.luben.zstd.{ Zstd, ZstdDecompressCtx, ZstdException }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.Attributes
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.stage.{ GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString

/** INTERNAL API */
@InternalApi private[pekko] object ZstdDecompressor {
  private val EmptyInput = ByteBuffer.allocate(0)
  private val NoInput: Iterator[ByteBuffer] = Iterator.empty

  // The level only applies to compression. Each materialization loads the dictionary into its own
  // context, which owns the digested dictionary and frees it on close. A shared ZstdDictDecompress
  // could not be closed when one materialization stops, and has no Cleaner, so it would only be freed
  // by finalization. The bytes are copied so that later changes to the caller's array have no effect.
  private def dictionaryBytes(dictionary: ZstdDictionaryImpl): Array[Byte] = {
    val bytes = dictionary.dictionary
    (dictionary.offset, dictionary.length) match {
      case (None, None)                 => bytes.clone()
      case (Some(offset), Some(length)) =>
        ZstdDictionaryImpl.requireSlice(bytes, offset, length)
        java.util.Arrays.copyOfRange(bytes, offset, offset + length)
      case _ =>
        throw new IllegalArgumentException("ZstdDictionary offset and length must be set together")
    }
  }
}

/** INTERNAL API */
@InternalApi private[pekko] class ZstdDecompressor(maxBytesPerChunk: Int, dictionary: Option[ZstdDictionaryImpl])
    extends SimpleLinearGraphStage[ByteString] {
  import ZstdDecompressor._

  require(maxBytesPerChunk > 0, "maxBytesPerChunk must be greater than 0")

  private val decompressDict = dictionary.map(dictionaryBytes)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogic(shape) with InHandler with OutHandler {

      private val decompressCtx = new ZstdDecompressCtx()
      // postStop is not called if creating the logic fails, so close the context here
      try decompressDict.foreach(decompressCtx.loadDict)
      catch {
        case NonFatal(e) =>
          decompressCtx.close()
          throw e
      }

      // zstd-jni supports heap ByteBuffers for streaming decompression, so no direct buffers are needed
      private var outputBuffer = ByteBuffer.allocate(maxBytesPerChunk)
      private var inputBuffer = EmptyInput
      // the remaining fragments of the current element, see ZstdBuffers.inputBuffers
      private var inputBuffers = NoInput
      // set when the last call filled the output buffer before the frame completed, zstd may then
      // still hold decompressed data. A completed frame is fully flushed, and another call would make
      // zstd wait for a new frame and report the stream as incomplete.
      private var outputPending = false
      // false while a frame has been started but not fully decompressed and flushed
      private var frameComplete = true

      override def onPush(): Unit = {
        inputBuffers = ZstdBuffers.inputBuffers(grab(in))
        decompress()
      }

      override def onPull(): Unit = decompress()

      override def onUpstreamFinish(): Unit =
        if (!hasInput && !outputPending) finish()
        else if (isAvailable(out)) decompress()
      // otherwise the remaining input is decompressed on the next onPull

      private def hasInput: Boolean = {
        while (!inputBuffer.hasRemaining && inputBuffers.hasNext) inputBuffer = inputBuffers.next()
        inputBuffer.hasRemaining
      }

      @tailrec
      private def decompress(): Unit =
        if (hasInput || outputPending) {
          frameComplete = decompressCtx.decompressByteBufferStream(outputBuffer, inputBuffer)
          outputPending = !frameComplete && !outputBuffer.hasRemaining
          if (outputBuffer.position() > 0) {
            val (output, nextBuffer) = ZstdBuffers.takeOutput(outputBuffer)
            outputBuffer = nextBuffer
            push(out, output)
          } else decompress()
        } else if (isClosed(in)) finish()
        else pull(in)

      private def finish(): Unit =
        if (frameComplete) completeStage()
        else failStage(new ZstdException(Zstd.errSrcSizeWrong(), "Truncated zstd stream"))

      override def postStop(): Unit = decompressCtx.close()

      setHandlers(in, out, this)
    }
  }

}
