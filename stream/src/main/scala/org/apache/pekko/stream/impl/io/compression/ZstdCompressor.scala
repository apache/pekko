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

import com.github.luben.zstd.{ EndDirective, ZstdCompressCtx, ZstdDictCompress }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.scaladsl.Compression
import pekko.util.{ ByteString, ByteStringBuilder }

/** INTERNAL API */
@InternalApi private[pekko] object ZstdCompressor {
  private val OutputBufferSize = 65536
  private val EmptyInput = ByteBuffer.allocate(0)
}

/**
 * INTERNAL API
 *
 * @param windowLog the log2 of the maximum window size, 0 uses the default of the compression level
 */
@InternalApi private[pekko] class ZstdCompressor(
    compressionLevel: Int = Compression.ZstdDefaultCompressionLevel,
    dictionary: Option[ZstdDictionaryImpl] = None,
    windowLog: Int = 0) extends Compressor {
  import ZstdCompressor._

  private val compressCtx = newCompressCtx()

  // A dictionary with a level is digested into a ZstdDictCompress, which has to be closed after the
  // context. Neither has a Cleaner, and the stage cannot close them in postStop if the compressor is
  // not created, so close them here if the setup fails.
  private val compressDict: Option[ZstdDictCompress] =
    try {
      compressCtx.setLevel(compressionLevel)
      if (windowLog != 0) compressCtx.setWindowLog(windowLog)
      dictionary.flatMap(loadDictionary)
    } catch {
      case NonFatal(e) =>
        compressCtx.close()
        throw e
    }

  // zstd-jni supports heap ByteBuffers for streaming compression, so no direct buffers are needed
  private var outputBuffer = ByteBuffer.allocate(OutputBufferSize)

  /** For tests, to observe that the context is closed. */
  private[pekko] def newCompressCtx(): ZstdCompressCtx = new ZstdCompressCtx()

  /** For tests, to observe that the dictionary is closed. */
  private[pekko] def compressDictionary: Option[ZstdDictCompress] = compressDict

  private def loadDictionary(dict: ZstdDictionaryImpl): Option[ZstdDictCompress] =
    (dict.level, dict.length, dict.offset) match {
      case (None, None, None) =>
        compressCtx.loadDict(dict.dictionary)
        None
      case (Some(dictLevel), None, None) =>
        Some(loadDictCompress(new ZstdDictCompress(dict.dictionary, dictLevel)))
      case (Some(dictLevel), Some(dictLength), Some(dictOffset)) =>
        Some(loadDictCompress(new ZstdDictCompress(dict.dictionary, dictOffset, dictLength, dictLevel)))
      case _ =>
        throw new IllegalArgumentException("Invalid combination of ZstdDictionary parameters")
    }

  private def loadDictCompress(dictCompress: ZstdDictCompress): ZstdDictCompress =
    try {
      compressCtx.loadDict(dictCompress)
      dictCompress
    } catch {
      case NonFatal(e) =>
        dictCompress.close()
        throw e
    }

  override def compress(input: ByteString): ByteString = compressStream(input, EndDirective.CONTINUE)

  override def flush(): ByteString = compressStream(ByteString.empty, EndDirective.FLUSH)

  override def finish(): ByteString = compressStream(ByteString.empty, EndDirective.END)

  override def compressAndFlush(input: ByteString): ByteString = compressStream(input, EndDirective.FLUSH)

  override def compressAndFinish(input: ByteString): ByteString = compressStream(input, EndDirective.END)

  override def close(): Unit =
    try compressCtx.close()
    finally compressDict.foreach(_.close())

  private def compressStream(input: ByteString, endOp: EndDirective): ByteString = {
    val builder = ByteString.newBuilder
    val inputBuffers = ZstdBuffers.inputBuffers(input)
    // only the last fragment of the input gets endOp, the ones before it are just compressed
    while (inputBuffers.hasNext) {
      val inputBuffer = inputBuffers.next()
      compressBuffer(inputBuffer, if (inputBuffers.hasNext) EndDirective.CONTINUE else endOp, builder)
    }
    if (input.isEmpty && endOp != EndDirective.CONTINUE)
      compressBuffer(EmptyInput, endOp, builder)
    builder.result()
  }

  private def compressBuffer(inputBuffer: ByteBuffer, endOp: EndDirective, builder: ByteStringBuilder): Unit = {
    var done = false
    while (!done) {
      val flushed = compressCtx.compressByteBufferStream(outputBuffer, inputBuffer, endOp)
      if (outputBuffer.position() > 0) {
        val (output, nextBuffer) = ZstdBuffers.takeOutput(outputBuffer)
        builder ++= output
        outputBuffer = nextBuffer
      }
      // with CONTINUE zstd may keep data in its internal buffers, otherwise loop until everything is flushed
      done = if (endOp == EndDirective.CONTINUE) !inputBuffer.hasRemaining else flushed
    }
  }
}
