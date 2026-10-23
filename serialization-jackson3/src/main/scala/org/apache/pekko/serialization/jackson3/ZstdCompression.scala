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

package org.apache.pekko.serialization.jackson3

import java.io.ByteArrayOutputStream

import com.github.luben.zstd.{ Zstd, ZstdInputStreamNoFinalizer }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.io.UnsynchronizedByteArrayInputStream

/**
 * INTERNAL API
 *
 * zstd compression of serialized payloads. zstd-jni is an optional dependency, so it is only
 * referenced from this object and only used when zstd is configured or a zstd payload is
 * deserialized. A missing zstd-jni (or a platform without its native library) is reported as an
 * `IllegalArgumentException` rather than a `LinkageError`.
 */
@InternalApi private[jackson3] object ZstdCompression {

  private final val MaxArraySize = Int.MaxValue - 8

  private def withZstd[T](action: String)(block: => T): T =
    try block
    catch {
      case e: LinkageError =>
        throw new IllegalArgumentException(
          s"zstd is required to $action but is not available. Add the optional dependency " +
          "com.github.luben:zstd-jni to the classpath.",
          e)
    }

  /** Fails if zstd-jni is not available or `level` is not a valid zstd compression level. */
  def checkLevel(level: Int): Unit = withZstd("compress payloads") {
    if (level < Zstd.minCompressionLevel() || level > Zstd.maxCompressionLevel())
      throw new IllegalArgumentException(
        s"Invalid zstd compression level [$level], it must be between [${Zstd.minCompressionLevel()}] " +
        s"and [${Zstd.maxCompressionLevel()}]")
  }

  def compress(bytes: Array[Byte], level: Int): Array[Byte] =
    withZstd("compress payloads") {
      Zstd.compress(bytes, level)
    }

  /**
   * Decompress a zstd payload, failing with an `IllegalArgumentException` once it would exceed
   * `maxDecompressedSize` bytes, so a small payload cannot expand without limit. A negative
   * `maxDecompressedSize` applies no limit. `settingPath` is named in the error message.
   */
  def decompress(bytes: Array[Byte], maxDecompressedSize: Long, settingPath: String): Array[Byte] =
    withZstd("deserialize a zstd compressed payload") {
      // Frames written by `compress` declare their decompressed size. Frames from other writers,
      // e.g. a streaming compressor, may not (0 or negative here), and are decompressed in chunks.
      val declaredSize = Zstd.getFrameContentSize(bytes)
      if (declaredSize > 0) {
        if (maxDecompressedSize >= 0 && declaredSize > maxDecompressedSize)
          throw new IllegalArgumentException(
            s"Compressed message declares decompressed size [$declaredSize] bytes, which exceeds the maximum " +
            s"of [$maxDecompressedSize] bytes ($settingPath)")
        if (declaredSize > MaxArraySize)
          throw new IllegalArgumentException(
            s"Compressed message declares decompressed size [$declaredSize] bytes, which is too large")
        // The declared size is the capacity of the output, so a frame that produces more than it
        // declares fails instead of growing beyond the checked size.
        Zstd.decompress(bytes, declaredSize.toInt)
      } else decompressStream(bytes, maxDecompressedSize, settingPath)
    }

  private def decompressStream(bytes: Array[Byte], maxDecompressedSize: Long, settingPath: String): Array[Byte] = {
    val in = new ZstdInputStreamNoFinalizer(new UnsynchronizedByteArrayInputStream(bytes))
    try {
      val out =
        new ByteArrayOutputStream(JacksonSerializer.initialDecompressBufferSize(bytes.length, maxDecompressedSize))
      val buffer = new Array[Byte](ZstdInputStreamNoFinalizer.recommendedDOutSize().toInt)
      var total = 0L
      var n = in.read(buffer)
      while (n != -1) {
        total += n
        if (maxDecompressedSize >= 0 && total > maxDecompressedSize)
          throw new IllegalArgumentException(
            s"Decompressed message exceeds the maximum of [$maxDecompressedSize] bytes ($settingPath)")
        out.write(buffer, 0, n)
        n = in.read(buffer)
      }
      out.toByteArray
    } finally in.close()
  }
}
