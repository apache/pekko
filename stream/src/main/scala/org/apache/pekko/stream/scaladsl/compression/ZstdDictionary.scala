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

package org.apache.pekko.stream.scaladsl.compression

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.impl.io.compression.ZstdDictionaryImpl
import pekko.util.ByteString

/**
 * Configuration class based on the official C zstd library to specify dictionary options for
 * compression.
 *
 * @see https://github.com/facebook/zstd?tab=readme-ov-file#dictionary-compression-how-to
 * @see https://facebook.github.io/zstd/zstd_manual.html#Chapter10
 * @since 2.0.0
 */
final case class ZstdDictionary private (dictionary: Array[Byte], level: Option[Int],
    offset: Option[Int], length: Option[Int]) { clazz =>

  @InternalApi
  private[pekko] def toImpl: ZstdDictionaryImpl = new ZstdDictionaryImpl {
    override val dictionary: Array[Byte] = clazz.dictionary
    override val level: Option[Int] = clazz.level
    override val offset: Option[Int] = clazz.offset
    override val length: Option[Int] = clazz.length
  }
}

/**
 * @since 2.0.0
 */
object ZstdDictionary {

  def apply(dictionary: Array[Byte]): ZstdDictionary =
    ZstdDictionary(dictionary, None, None, None)

  def apply(dictionary: Array[Byte], level: Int): ZstdDictionary =
    ZstdDictionary(dictionary, Some(level), None, None)

  def apply(dictionary: Array[Byte], level: Int, offset: Int, length: Int): ZstdDictionary =
    ZstdDictionary(dictionary, Some(level), Some(offset), Some(length))

  def apply(dictionary: ByteString): ZstdDictionary =
    ZstdDictionary(dictionary.toArray, None, None, None)

  def apply(dictionary: ByteString, level: Int)
      : ZstdDictionary =
    ZstdDictionary(dictionary.toArray, level)

  def apply(dictionary: ByteString, level: Int, offset: Int, length: Int)
      : ZstdDictionary =
    ZstdDictionary(dictionary.toArray, level, offset, length)

  def apply(dictionary: String): ZstdDictionary =
    ZstdDictionary(dictionary.getBytes)

  def apply(dictionary: String, level: Int): ZstdDictionary =
    ZstdDictionary(dictionary.getBytes, level)

  def apply(dictionary: String, level: Int, offset: Int, length: Int): ZstdDictionary =
    ZstdDictionary(dictionary.getBytes, level, offset, length)
}
