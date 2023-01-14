/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.scaladsl
import pekko.util.ByteString

object Compression {

  /**
   * Creates a Flow that decompresses gzip-compressed stream of data.
   *
   * @param maxBytesPerChunk Maximum length of the output [[ByteString]] chunk.
   */
  def gunzip(maxBytesPerChunk: Int): Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.gunzip(maxBytesPerChunk).asJava

  /**
   * Creates a Flow that decompresses deflate-compressed stream of data.
   *
   * @param maxBytesPerChunk Maximum length of the output [[ByteString]] chunk.
   */
  def inflate(maxBytesPerChunk: Int): Flow[ByteString, ByteString, NotUsed] =
    inflate(maxBytesPerChunk, false)

  /**
   * Same as [[inflate]] with configurable maximum output length and nowrap
   *
   * @param maxBytesPerChunk Maximum length of the output [[ByteString]] chunk.
   * @param nowrap if true then use GZIP compatible decompression
   */
  def inflate(maxBytesPerChunk: Int, nowrap: Boolean): Flow[ByteString, ByteString, NotUsed] =
    scaladsl.Compression.inflate(maxBytesPerChunk, nowrap).asJava

  /**
   * Creates a flow that gzip-compresses a stream of ByteStrings. Note that the compressor
   * will SYNC_FLUSH after every [[ByteString]] so that it is guaranteed that every [[ByteString]]
   * coming out of the flow can be fully decompressed without waiting for additional data. This may
   * come at a compression performance cost for very small chunks.
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
   * Creates a flow that deflate-compresses a stream of ByteString. Note that the compressor
   * will SYNC_FLUSH after every [[ByteString]] so that it is guaranteed that every [[ByteString]]
   * coming out of the flow can be fully decompressed without waiting for additional data. This may
   * come at a compression performance cost for very small chunks.
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

}
