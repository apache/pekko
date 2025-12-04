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
import com.github.luben.zstd.{ ZstdDictCompress, ZstdDirectBufferCompressingStreamNoFinalizer }
import org.apache.pekko
import org.apache.pekko.stream.scaladsl.Compression
import pekko.annotation.InternalApi
import pekko.util.ByteString

/** INTERNAL API */
@InternalApi private[pekko] class ZstdCompressor(
    compressionLevel: Int =
      Compression.ZstdDefaultCompressionLevel, dictionary: Option[ZstdDictionaryImpl] = None) extends Compressor {

  private val targetBuffer = ByteBuffer.allocateDirect(65536)
  private val compressingStream = new ZstdDirectBufferCompressingStreamNoFinalizer(targetBuffer, compressionLevel)

  dictionary.foreach { dict =>
    (dict.level, dict.length, dict.offset) match {
      case (None, None, None) =>
        compressingStream.setDict(dict.dictionary)
      case (Some(dictLevel), None, None) =>
        compressingStream.setDict(new ZstdDictCompress(dict.dictionary, dictLevel))
      case (Some(dictLevel), Some(dictLength), Some(dictOffset)) =>
        compressingStream.setDict(new ZstdDictCompress(dict.dictionary, dictLevel, dictLength, dictOffset))
      case _ =>
        throw new IllegalArgumentException("Invalid combination of ZstdDictionary parameters")
    }
  }

  override def compress(input: ByteString): ByteString = {
    val inputBB = ByteBuffer.allocateDirect(input.size)
    inputBB.put(input.toArrayUnsafe())
    inputBB.flip()
    compressingStream.compress(inputBB)
    val result = ByteString.fromByteBuffer(targetBuffer)
    targetBuffer.flip()
    result
  }

  override def flush(): ByteString = {
    targetBuffer.flip()
    val result = ByteString.fromByteBuffer(targetBuffer)
    targetBuffer.clear()
    compressingStream.flush()
    result
  }

  override def finish(): ByteString = {
    compressingStream.close()
    targetBuffer.flip()
    val arr = Array.ofDim[Byte](targetBuffer.limit())
    targetBuffer.get(arr)
    val result = ByteString.fromArrayUnsafe(arr)
    result
  }

  override def compressAndFlush(input: ByteString): ByteString = {
    val inputBB = ByteBuffer.allocateDirect(input.size)
    inputBB.put(input.toArrayUnsafe())
    inputBB.flip()
    compressingStream.compress(inputBB)
    compressingStream.flush()
    targetBuffer.flip()

    val arr = new Array[Byte](targetBuffer.limit())
    targetBuffer.get(arr)
    targetBuffer.clear()
    ByteString.fromArrayUnsafe(arr)
  }

  override def compressAndFinish(input: ByteString): ByteString = {
    val inputBB = ByteBuffer.allocateDirect(input.size)
    inputBB.put(input.toArrayUnsafe())
    inputBB.flip()
    compressingStream.compress(inputBB)
    compressingStream.close()
    targetBuffer.flip()

    val arr = new Array[Byte](targetBuffer.limit())
    targetBuffer.get(arr)
    ByteString.fromArrayUnsafe(arr)
  }

  override def close(): Unit = compressingStream.close()
}
