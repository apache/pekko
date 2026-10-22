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

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString

/**
 * INTERNAL API
 *
 * zstd-jni accepts array-backed heap ByteBuffers for streaming (de)compression, these helpers
 * move data between ByteStrings and such buffers while avoiding array copies where possible.
 */
@InternalApi private[compression] object ZstdBuffers {

  /**
   * The fragments of `input` as heap buffers that wrap the backing arrays without copying, so
   * that a multi-fragment ByteString does not need to be compacted. zstd only reads from them.
   */
  def inputBuffers(input: ByteString): Iterator[ByteBuffer] = input.asByteBuffersUnsafe.iterator

  /**
   * Takes the bytes written to `output` (from 0 until its position) as a ByteString. When at
   * least half of the buffer is used its array is handed over without copying and a new buffer
   * is returned for further output, otherwise the bytes are copied so that a mostly empty
   * array is not retained.
   */
  def takeOutput(output: ByteBuffer): (ByteString, ByteBuffer) = {
    val produced = output.position()
    if (produced >= output.capacity() / 2)
      (ByteString.fromArrayUnsafe(output.array(), 0, produced), ByteBuffer.allocate(output.capacity()))
    else {
      output.clear()
      (ByteString.fromArray(output.array(), 0, produced), output)
    }
  }
}
