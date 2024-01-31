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

package org.apache.pekko.util

import java.io.{ IOException, InputStream }
import java.nio.ByteBuffer

/**
 * Simple {@link InputStream} implementation that exposes currently
 * available content of a {@link ByteBuffer}.
 *
 * Derived from https://github.com/FasterXML/jackson-databind/blob/1e73db1fabd181937c68b49ffc502fb7f614d0c2/src/main/java/com/fasterxml/jackson/databind/util/ByteBufferBackedInputStream.java
 */
private[util] class ByteBufferBackedInputStream(bb: ByteBuffer) extends InputStream {

  override def available: Int = bb.remaining

  @throws[IOException]
  override def read: Int = {
    if (bb.hasRemaining) bb.get & 0xFF
    else -1
  }

  @throws[IOException]
  override def read(bytes: Array[Byte], off: Int, len: Int): Int = {
    if (!bb.hasRemaining) {
      -1
    } else {
      val newLen = Math.min(len, bb.remaining)
      bb.get(bytes, off, newLen)
      newLen
    }
  }
}
