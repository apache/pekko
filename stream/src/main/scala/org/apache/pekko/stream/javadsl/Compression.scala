/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl

import java.util.Optional

import scala.jdk.OptionConverters.RichOptional

import com.github.luben.zstd._

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.impl.io.compression.{ CompressionUtils, ZstdCompressor }
import pekko.stream.javadsl.compression.ZstdDictionary
import pekko.stream.scaladsl
import pekko.util.ByteString

object Compression {

  /**
   * Creates a Flow that decompresses gzip-compressed stream of data.
   *
   * @param maxBytesPerChunk Maximum length of the output [[pekko.util.ByteString]] chunk.
   * @since 1.3.0
   */
  def gzipDecompress(maxBytesPerChunk: Int): Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.gzipDecompress(maxBytesPerChunk).asJava

  /**
   * Creates a Flow that decompresses deflate-compressed stream of data.
   *
   * @param maxBytesPerChunk Maximum length of the output [[pekko.util.ByteString]] chunk.
   */
  def inflate(maxBytesPerChunk: Int): Flow[ByteString, ByteString, NotUsed] =
    inflate(maxBytesPerChunk, false)

  /**
   * Same as [[inflate]] with configurable maximum output length and nowrap
   *
   * @param maxBytesPerChunk Maximum length of the output [[pekko.util.ByteString]] chunk.
   * @param nowrap if true then use GZIP compatible decompression
   */
  def inflate(maxBytesPerChunk: Int, nowrap: Boolean): Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.inflate(maxBytesPerChunk, nowrap).asJava

  /**
   * Creates a flow that gzip-compresses a stream of ByteStrings. Note that the compressor will
   * flush after every single element in stream so that it is guaranteed that every [[pekko.util.ByteString]]
   * coming out of the flow can be fully decompressed without waiting for additional data. This may come at
   * a compression performance cost for very small chunks.
   */
  def gzip: Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.gzip.asJava

  /**
   * Same as [[gzip]] with a custom level.
   *
   * @param level Compression level (0-9)
   */
  def gzip(level: Int): Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.gzip(level).asJava

  /**
   * Same as [[gzip]] with a custom level and configurable flush mode.
   *
   * @param level Compression level (0-9)
   * @param autoFlush If true will automatically flush after every single element in the stream.
   *
   * @since 1.3.0
   */
  def gzip(level: Int, autoFlush: Boolean): Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.gzip(level, autoFlush).asJava

  /**
   * Creates a flow that deflate-compresses a stream of ByteString. Note that the compressor will
   * flush after every single element in stream so that it is guaranteed that every [[pekko.util.ByteString]]
   * coming out of the flow can be fully decompressed without waiting for additional data. This may come at
   * a compression performance cost for very small chunks.
   */
  def deflate: Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.deflate.asJava

  /**
   * Same as [[deflate]] with configurable level and nowrap
   *
   * @param level Compression level (0-9)
   * @param nowrap if true then use GZIP compatible compression
   */
  def deflate(level: Int, nowrap: Boolean): Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.deflate(level, nowrap).asJava

  /**
   * Same as [[deflate]] with configurable level, nowrap and autoFlush.
   *
   * @param level Compression level (0-9)
   * @param nowrap if true then use GZIP compatible compression
   * @param autoFlush If true will automatically flush after every single element in the stream.
   *
   * @since 1.3.0
   */
  def deflate(level: Int, nowrap: Boolean, autoFlush: Boolean): Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.deflate(level, nowrap, autoFlush).asJava

  /**
   * The minimum compression level supported by zstd
   * @since 2.0.0
   */
  final val ZstdMinCompressionLevel: Int = Zstd.minCompressionLevel()

  /**
   * The maximum compression level supported by zstd
   * @since 2.0.0
   */
  final val ZstdMaxCompressionLevel: Int = Zstd.maxCompressionLevel()

  /**
   * The zstd default compression level
   * @since 2.0.0
   */
  final val ZstdDefaultCompressionLevel: Int = Zstd.defaultCompressionLevel()

  /**
   * @since 2.0.0
   */
  def zstd: Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.zstd.asJava

  /**
   * Same as [[zstd]] with a custom level and an optional dictionary.
   *
   * @param level      The compression level, must be greater or equal to [[ZstdMinCompressionLevel]] and less than or equal
   *                   to [[ZstdMaxCompressionLevel]]
   * @param dictionary An optional dictionary that can be used for compression
   * @since 2.0.0
   */
  def zstd(level: Int, dictionary: Optional[ZstdDictionary]): Flow[ByteString, ByteString, NotUsed] = {
    require(level <= ZstdMaxCompressionLevel && level >= ZstdMinCompressionLevel)
    CompressionUtils.compressorFlow(() => new ZstdCompressor(level, dictionary.toScala.map(_.toImpl))).asJava
  }

  /**
   * Same as [[zstd]] with a custom level, optional dictionary and configurable flush mode.
   *
   * @param level      The compression level, must be greater or equal to [[ZstdMinCompressionLevel]] and less than or equal
   *                   to [[ZstdMaxCompressionLevel]]
   * @param dictionary An optional dictionary that can be used for compression
   * @param autoFlush  If true will automatically flush after every single element in the stream.
   * @since 2.0.0
   */
  def zstd(level: Int, dictionary: Optional[ZstdDictionary], autoFlush: Boolean)
      : Flow[ByteString, ByteString, NotUsed] = {
    require(level <= ZstdMaxCompressionLevel && level >= ZstdMinCompressionLevel)
    CompressionUtils.compressorFlow(() => new ZstdCompressor(level, dictionary.toScala.map(_.toImpl)), autoFlush).asJava
  }

  /**
   * The maximum block size used by zstd decompression
   * @since 2.0.0
   */
  final val ZstdDecompressMaxBlockSize: Int = Zstd.blockSizeMax()

  /**
   * @since 2.0.0
   */
  def zstdDecompress(maxBytesPerChunk: Int): Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.zstdDecompress(maxBytesPerChunk).asJava
}
