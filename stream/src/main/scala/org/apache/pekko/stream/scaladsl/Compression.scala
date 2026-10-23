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

package org.apache.pekko.stream.scaladsl

import java.util.zip.Deflater

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.ApiMayChange
import pekko.stream.{ ZstdCompressionSettings, ZstdDecompressionSettings }
import pekko.stream.impl.io.compression._
import pekko.stream.scaladsl.compression.ZstdDictionary
import pekko.util.ByteString

object Compression {
  final val MaxBytesPerChunkDefault = 64 * 1024

  /**
   * Creates a flow that gzip-compresses a stream of ByteStrings. Note that the compressor will
   * flush after every single element in stream so that it is guaranteed that every [[pekko.util.ByteString]]
   * coming out of the flow can be fully decompressed without waiting for additional data. This may come at
   * a compression performance cost for very small chunks.
   */
  def gzip: Flow[ByteString, ByteString, NotUsed] = gzip(Deflater.BEST_COMPRESSION)

  /**
   * Same as `gzip` with a custom level.
   *
   * @param level Compression level (0-9)
   */
  def gzip(level: Int): Flow[ByteString, ByteString, NotUsed] =
    CompressionUtils.compressorFlow(() => new GzipCompressor(level))

  /**
   * Same as `gzip` with a custom level and configurable flush mode.
   *
   * @param level Compression level (0-9)
   * @param autoFlush If true will automatically flush after every single element in the stream.
   *
   * @since 1.3.0
   */
  def gzip(level: Int, autoFlush: Boolean): Flow[ByteString, ByteString, NotUsed] =
    CompressionUtils.compressorFlow(() => new GzipCompressor(level), autoFlush)

  /**
   * Creates a Flow that decompresses a gzip-compressed stream of data.
   *
   * @param maxBytesPerChunk Maximum length of an output [[pekko.util.ByteString]] chunk.
   * @since 1.3.0
   */
  def gzipDecompress(maxBytesPerChunk: Int = MaxBytesPerChunkDefault): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString].via(new GzipDecompressor(maxBytesPerChunk)).named("gzipDecompress")

  /**
   * Creates a flow that deflate-compresses a stream of ByteString. Note that the compressor will
   * flush after every single element in stream so that it is guaranteed that every [[pekko.util.ByteString]]
   * coming out of the flow can be fully decompressed without waiting for additional data. This may come at
   * a compression performance cost for very small chunks.
   */
  def deflate: Flow[ByteString, ByteString, NotUsed] = deflate(Deflater.BEST_COMPRESSION, nowrap = false)

  /**
   * Same as `deflate` with configurable level and nowrap
   *
   * @param level Compression level (0-9)
   * @param nowrap if true then use GZIP compatible compression
   */
  def deflate(level: Int, nowrap: Boolean): Flow[ByteString, ByteString, NotUsed] =
    CompressionUtils.compressorFlow(() => new DeflateCompressor(level, nowrap))

  /**
   * Same as `deflate` with configurable level, nowrap and autoFlush.
   *
   * @param level Compression level (0-9)
   * @param nowrap if true then use GZIP compatible compression
   * @param autoFlush If true will automatically flush after every single element in the stream.
   *
   * @since 1.3.0
   */
  def deflate(level: Int, nowrap: Boolean, autoFlush: Boolean): Flow[ByteString, ByteString, NotUsed] =
    CompressionUtils.compressorFlow(() => new DeflateCompressor(level, nowrap), autoFlush)

  /**
   * Creates a Flow that decompresses a deflate-compressed stream of data.
   *
   * @param maxBytesPerChunk Maximum length of an output [[pekko.util.ByteString]] chunk.
   */
  def inflate(maxBytesPerChunk: Int = MaxBytesPerChunkDefault): Flow[ByteString, ByteString, NotUsed] =
    inflate(maxBytesPerChunk, false)

  /**
   * Creates a Flow that decompresses a deflate-compressed stream of data.
   *
   * @param maxBytesPerChunk Maximum length of an output [[pekko.util.ByteString]] chunk.
   * @param nowrap if true then use GZIP compatible decompression
   */
  def inflate(maxBytesPerChunk: Int, nowrap: Boolean): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString].via(new DeflateDecompressor(maxBytesPerChunk, nowrap)).named("inflate")

  // The zstd constants below are copies of libzstd's values instead of calls to zstd-jni (e.g.
  // Zstd.minCompressionLevel()). zstd-jni is an optional dependency: calling it here would make
  // initializing this object, and so using gzip or deflate, fail with NoClassDefFoundError when
  // zstd-jni is not on the classpath, and would load the native library even for those users.
  // ZstdSpec checks that they still match zstd-jni, so a zstd-jni upgrade that changes one of
  // them fails the tests.

  /**
   * The minimum compression level supported by zstd
   * @since 2.0.0
   */
  // ZSTD_minCLevel() is -ZSTD_TARGETLENGTH_MAX, which is ZSTD_BLOCKSIZE_MAX (1 << 17) in libzstd
  @ApiMayChange
  final val ZstdMinCompressionLevel: Int = -(1 << 17)

  /**
   * The maximum compression level supported by zstd
   * @since 2.0.0
   */
  // ZSTD_maxCLevel() is ZSTD_MAX_CLEVEL in libzstd
  @ApiMayChange
  final val ZstdMaxCompressionLevel: Int = 22

  /**
   * The zstd default compression level
   * @since 2.0.0
   */
  // ZSTD_defaultCLevel() is ZSTD_CLEVEL_DEFAULT in libzstd
  @ApiMayChange
  final val ZstdDefaultCompressionLevel: Int = 3

  /**
   * The minimum window log supported by zstd compression
   * @since 2.0.0
   */
  // ZSTD_WINDOWLOG_MIN in libzstd
  @ApiMayChange
  final val ZstdMinWindowLog: Int = 10

  /**
   * The maximum window log supported by zstd compression on 64-bit platforms
   * @since 2.0.0
   */
  // ZSTD_WINDOWLOG_MAX_64 in libzstd
  @ApiMayChange
  final val ZstdMaxWindowLog: Int = 31

  /**
   * Creates a flow that zstd-compresses a stream of ByteStrings, with the default compression level.
   *
   * @since 2.0.0
   */
  @ApiMayChange
  def zstd: Flow[ByteString, ByteString, NotUsed] = zstd(ZstdCompressionSettings())

  /**
   * Same as [[zstd]] with a custom level and an optional dictionary.
   *
   * @param level      The compression level, must be greater or equal to [[ZstdMinCompressionLevel]] and less than or equal
   *                   to [[ZstdMaxCompressionLevel]]
   * @param dictionary An optional dictionary that can be used for compression
   * @since 2.0.0
   */
  @ApiMayChange
  def zstd(level: Int, dictionary: Option[ZstdDictionary]): Flow[ByteString, ByteString, NotUsed] = {
    val settings = ZstdCompressionSettings().withLevel(level)
    zstd(dictionary.fold(settings)(settings.withDictionary))
  }

  /**
   * Same as [[zstd]] with the given settings, see [[pekko.stream.ZstdCompressionSettings]] for the level, flush
   * mode, window size and dictionary.
   *
   * @since 2.0.0
   */
  @ApiMayChange
  def zstd(settings: ZstdCompressionSettings): Flow[ByteString, ByteString, NotUsed] =
    CompressionUtils.compressorFlow(
      () => new ZstdCompressor(settings.level, settings.dictionaryImpl, settings.windowLog),
      settings.autoFlush)

  /**
   * The maximum block size used by zstd decompression
   * @since 2.0.0
   */
  // ZSTD_BLOCKSIZE_MAX in libzstd is 1 << ZSTD_BLOCKSIZELOG_MAX (17), i.e. 128 KiB
  @ApiMayChange
  final val ZstdDecompressMaxBlockSize: Int = 1 << 17

  /**
   * Creates a flow that zstd-decompresses a stream of ByteStrings.
   *
   * @param maxBytesPerChunk The maximum length of the emitted ByteStrings
   * @since 2.0.0
   */
  @ApiMayChange
  def zstdDecompress(maxBytesPerChunk: Int = ZstdDecompressMaxBlockSize): Flow[ByteString, ByteString, NotUsed] =
    zstdDecompress(ZstdDecompressionSettings().withMaxBytesPerChunk(maxBytesPerChunk))

  /**
   * Same as [[zstdDecompress]] with the given settings, see [[pekko.stream.ZstdDecompressionSettings]] for the chunk
   * size and dictionary.
   *
   * @since 2.0.0
   */
  @ApiMayChange
  def zstdDecompress(settings: ZstdDecompressionSettings): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString]
      .via(new ZstdDecompressor(settings.maxBytesPerChunk, settings.dictionaryImpl))
      .named("zstdDecompress")
}
