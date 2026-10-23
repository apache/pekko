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
 * Settings for the zstd compression operators, `Compression.zstd` in the Scala and Java DSLs.
 *
 * @since 2.0.0
 */
@ApiMayChange
final class ZstdCompressionSettings private (
    val level: Int,
    val autoFlush: Boolean,
    val windowLog: Int,
    dictionary: Option[ZstdDictionaryImpl]) {

  require(
    level >= Compression.ZstdMinCompressionLevel && level <= Compression.ZstdMaxCompressionLevel,
    s"level must be between ${Compression.ZstdMinCompressionLevel} and ${Compression.ZstdMaxCompressionLevel}, " +
    s"was $level")
  require(
    windowLog == 0 || (windowLog >= Compression.ZstdMinWindowLog && windowLog <= Compression.ZstdMaxWindowLog),
    s"windowLog must be 0 or between ${Compression.ZstdMinWindowLog} and ${Compression.ZstdMaxWindowLog}, " +
    s"was $windowLog")

  /**
   * The compression level, must be greater or equal to `Compression.ZstdMinCompressionLevel` and less than or equal
   * to `Compression.ZstdMaxCompressionLevel`. Defaults to `Compression.ZstdDefaultCompressionLevel`.
   */
  def withLevel(level: Int): ZstdCompressionSettings = copy(level = level)

  /**
   * If true (the default) the compressor flushes after every single element in the stream, so that every emitted
   * `ByteString` can be fully decompressed without waiting for more data. If false, the compressor only emits output
   * when the compression algorithm produces it, which can improve the compression ratio for small elements.
   */
  def withAutoFlush(autoFlush: Boolean): ZstdCompressionSettings = copy(autoFlush = autoFlush)

  /**
   * The log2 of the maximum window size, which a decompressor needs to buffer. 0 (the default) uses the default of
   * the compression level, otherwise it must be greater or equal to `Compression.ZstdMinWindowLog` and less than or
   * equal to `Compression.ZstdMaxWindowLog`. For example, RFC 9659 limits the HTTP `zstd` content-coding to a window
   * log of 23 (8 MiB).
   */
  def withWindowLog(windowLog: Int): ZstdCompressionSettings = copy(windowLog = windowLog)

  /**
   * Scala API: compress with a dictionary. The output can only be decompressed with the same dictionary.
   */
  def withDictionary(dictionary: scaladsl.compression.ZstdDictionary): ZstdCompressionSettings =
    copy(dictionary = Some(dictionary.toImpl))

  /**
   * Java API: compress with a dictionary. The output can only be decompressed with the same dictionary.
   */
  def withDictionary(dictionary: javadsl.compression.ZstdDictionary): ZstdCompressionSettings =
    copy(dictionary = Some(dictionary.toImpl))

  /**
   * Compress without a dictionary, the default.
   */
  def withoutDictionary: ZstdCompressionSettings = copy(dictionary = None)

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] def dictionaryImpl: Option[ZstdDictionaryImpl] = dictionary

  override def toString: String =
    s"ZstdCompressionSettings(level=$level,autoFlush=$autoFlush,windowLog=$windowLog,dictionary=${dictionary.isDefined})"

  private def copy(
      level: Int = level,
      autoFlush: Boolean = autoFlush,
      windowLog: Int = windowLog,
      dictionary: Option[ZstdDictionaryImpl] = dictionary): ZstdCompressionSettings =
    new ZstdCompressionSettings(level, autoFlush, windowLog, dictionary)
}

/**
 * @since 2.0.0
 */
@ApiMayChange
object ZstdCompressionSettings {

  private val Default =
    new ZstdCompressionSettings(Compression.ZstdDefaultCompressionLevel, autoFlush = true, windowLog = 0, None)

  /**
   * Scala API: the default settings, the default compression level with auto flush, the default window size of the
   * level and no dictionary
   */
  def apply(): ZstdCompressionSettings = Default

  /**
   * Java API: the default settings, the default compression level with auto flush, the default window size of the
   * level and no dictionary
   */
  def create(): ZstdCompressionSettings = Default
}
