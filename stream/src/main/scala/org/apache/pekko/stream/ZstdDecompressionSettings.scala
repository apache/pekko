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

package org.apache.pekko.stream

import org.apache.pekko
import pekko.annotation.{ ApiMayChange, InternalApi }
import pekko.stream.impl.io.compression.ZstdDictionaryImpl
import pekko.stream.scaladsl.Compression

/**
 * Settings for the zstd decompression operators, `Compression.zstdDecompress` in the Scala and Java DSLs.
 *
 * @since 2.0.0
 */
@ApiMayChange
final class ZstdDecompressionSettings private (
    val maxBytesPerChunk: Int,
    val windowLogMax: Int,
    dictionary: Option[ZstdDictionaryImpl]) {

  require(maxBytesPerChunk > 0, s"maxBytesPerChunk must be greater than 0, was $maxBytesPerChunk")
  require(
    windowLogMax == 0 || (windowLogMax >= Compression.ZstdMinWindowLog && windowLogMax <= Compression.ZstdMaxWindowLog),
    s"windowLogMax must be 0 or between ${Compression.ZstdMinWindowLog} and ${Compression.ZstdMaxWindowLog}, " +
    s"was $windowLogMax")

  /**
   * The maximum length of the emitted `ByteString`s. Defaults to `Compression.ZstdDecompressMaxBlockSize`.
   */
  def withMaxBytesPerChunk(maxBytesPerChunk: Int): ZstdDecompressionSettings =
    copy(maxBytesPerChunk = maxBytesPerChunk)

  /**
   * The log2 of the largest window that a frame may use, frames that need a larger window fail the stream before
   * the window buffer is allocated. 0 (the default) uses the libzstd limit of 27 (128 MiB), otherwise it must be
   * greater or equal to `Compression.ZstdMinWindowLog` and less than or equal to `Compression.ZstdMaxWindowLog`.
   * For example, RFC 9659 limits the HTTP `zstd` content-coding to a window log of 23 (8 MiB).
   *
   * A value other than 0 requires zstd-jni 1.5.7-25 or later. With an older version, materializing the
   * decompression operator fails with an `UnsupportedOperationException`.
   */
  def withWindowLogMax(windowLogMax: Int): ZstdDecompressionSettings = copy(windowLogMax = windowLogMax)

  /**
   * Scala API: decompress with a dictionary, which must be the one used for compression. Its level is ignored.
   */
  def withDictionary(dictionary: scaladsl.compression.ZstdDictionary): ZstdDecompressionSettings =
    copy(dictionary = Some(dictionary.toImpl))

  /**
   * Java API: decompress with a dictionary, which must be the one used for compression. Its level is ignored.
   */
  def withDictionary(dictionary: javadsl.compression.ZstdDictionary): ZstdDecompressionSettings =
    copy(dictionary = Some(dictionary.toImpl))

  /**
   * Decompress without a dictionary, the default.
   */
  def withoutDictionary: ZstdDecompressionSettings = copy(dictionary = None)

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] def dictionaryImpl: Option[ZstdDictionaryImpl] = dictionary

  override def toString: String =
    s"ZstdDecompressionSettings(maxBytesPerChunk=$maxBytesPerChunk,windowLogMax=$windowLogMax," +
    s"dictionary=${dictionary.isDefined})"

  private def copy(
      maxBytesPerChunk: Int = maxBytesPerChunk,
      windowLogMax: Int = windowLogMax,
      dictionary: Option[ZstdDictionaryImpl] = dictionary): ZstdDecompressionSettings =
    new ZstdDecompressionSettings(maxBytesPerChunk, windowLogMax, dictionary)
}

/**
 * @since 2.0.0
 */
@ApiMayChange
object ZstdDecompressionSettings {

  private val Default = new ZstdDecompressionSettings(Compression.ZstdDecompressMaxBlockSize, windowLogMax = 0, None)

  /**
   * Scala API: the default settings, chunks of at most `Compression.ZstdDecompressMaxBlockSize` bytes, the libzstd
   * window limit and no dictionary
   */
  def apply(): ZstdDecompressionSettings = Default

  /**
   * Java API: the default settings, chunks of at most `Compression.ZstdDecompressMaxBlockSize` bytes, the libzstd
   * window limit and no dictionary
   */
  def create(): ZstdDecompressionSettings = Default
}
