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

package org.apache.pekko.stream.impl.io

import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * INTERNAL API.
 */
@InternalApi private[stream] object TlsEngineHelpers {

  @inline
  def emptyReadBuffer(buffer: ByteBuffer): Unit = {
    buffer.clear().flip()
  }

  @inline
  def prepareForAppend(buffer: ByteBuffer): Unit =
    if (buffer.hasRemaining) buffer.compact()
    else buffer.clear()

  @inline
  def runDelegatedTasks(engine: SSLEngine): Int = {
    var count = 0
    var task = engine.getDelegatedTask
    while (task ne null) {
      count += 1
      task.run()
      task = engine.getDelegatedTask
    }
    count
  }

  @inline
  def hasCompleteTlsRecord(buffer: ByteBuffer): Boolean = {
    val remaining = buffer.remaining()
    if (remaining < TlsRecordHeaderSize) false
    else {
      val position = buffer.position()
      val contentType = buffer.get(position) & 0xFF
      val majorVersion = buffer.get(position + 1) & 0xFF

      if (majorVersion != TlsMajorVersion || contentType < TlsMinContentType || contentType > TlsMaxContentType) true
      else {
        val packetLength = ((buffer.get(position + 3) & 0xFF) << 8) | (buffer.get(position + 4) & 0xFF)
        val frameLength = packetLength + TlsRecordHeaderSize
        frameLength > buffer.capacity || remaining >= frameLength
      }
    }
  }

  private final val TlsRecordHeaderSize = 5
  private final val TlsMajorVersion = 3
  private final val TlsMinContentType = 20
  private final val TlsMaxContentType = 23
}
