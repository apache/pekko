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

import com.github.luben.zstd.{ ZstdCompressCtx, ZstdDecompressCtx, ZstdDictCompress, ZstdDictContentType }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.ZstdDictionaryContentType

/**
 * INTERNAL API
 *
 * The only class that references `ZstdDictContentType`, which was added in zstd-jni 1.5.7-26. zstd-jni is an
 * optional dependency that users add themselves, so an older version may be on the classpath. This object is only
 * used for content types other than [[ZstdDictionaryContentType.AUTO]], so that [[ZstdCompressor]] and
 * [[ZstdDecompressor]] keep working with older zstd-jni versions, and its signatures only use types that are also
 * in older zstd-jni versions.
 */
@InternalApi private[compression] object ZstdTypedDictionary {
  private def toZstd(contentType: ZstdDictionaryContentType): ZstdDictContentType = contentType match {
    case ZstdDictionaryContentType.RAW_CONTENT => ZstdDictContentType.RAW_CONTENT
    case ZstdDictionaryContentType.FULL_DICT   => ZstdDictContentType.FULL_DICT
    case other                                 =>
      throw new IllegalArgumentException(s"Unsupported ZstdDictionary content type [$other]")
  }

  def loadDict(ctx: ZstdCompressCtx, bytes: Array[Byte], contentType: ZstdDictionaryContentType): Unit =
    ctx.loadDict(bytes, toZstd(contentType))

  def loadDict(ctx: ZstdDecompressCtx, bytes: Array[Byte], contentType: ZstdDictionaryContentType): Unit =
    ctx.loadDict(bytes, toZstd(contentType))

  def dictCompress(bytes: Array[Byte], level: Int, contentType: ZstdDictionaryContentType): ZstdDictCompress =
    new ZstdDictCompress(bytes, level, toZstd(contentType))

  def dictCompress(bytes: Array[Byte], offset: Int, length: Int, level: Int,
      contentType: ZstdDictionaryContentType): ZstdDictCompress =
    new ZstdDictCompress(bytes, offset, length, level, toZstd(contentType))
}
