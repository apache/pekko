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

import org.apache.pekko
import pekko.annotation.{ InternalApi, InternalStableApi }
import pekko.stream.ZstdDictionaryContentType

@InternalStableApi
private[pekko] abstract class ZstdDictionaryImpl {
  def dictionary: Array[Byte]
  def level: Option[Int]
  def offset: Option[Int]
  def length: Option[Int]
  // not abstract, so that existing implementations keep working
  def contentType: ZstdDictionaryContentType = ZstdDictionaryContentType.AUTO
}

/** INTERNAL API */
@InternalApi private[pekko] object ZstdDictionaryImpl {
  def requireSlice(bytes: Array[Byte], offset: Int, length: Int): Unit =
    require(
      offset >= 0 && length >= 0 && offset <= bytes.length - length,
      s"ZstdDictionary offset [$offset] and length [$length] must be within the dictionary of " +
      s"[${bytes.length}] bytes")

  /**
   * Runs `auto` for [[ZstdDictionaryContentType.AUTO]], which uses the zstd-jni methods without a content type, and
   * otherwise `typed`, which must use [[ZstdTypedDictionary]]. zstd-jni is an optional dependency that users add
   * themselves, so a version older than 1.5.7-26, without `ZstdDictContentType`, may be on the classpath.
   */
  def withContentType[T](contentType: ZstdDictionaryContentType)(auto: => T)(typed: => T): T =
    if (contentType == ZstdDictionaryContentType.AUTO) auto
    else
      try typed
      catch {
        case e @ (_: NoClassDefFoundError | _: NoSuchMethodError) =>
          throw new UnsupportedOperationException(
            s"ZstdDictionary content type [$contentType] requires zstd-jni 1.5.7-26 or later",
            e)
      }
}
