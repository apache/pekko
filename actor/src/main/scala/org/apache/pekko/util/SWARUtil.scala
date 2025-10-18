/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.pekko.util

import java.lang.invoke.MethodHandles

import org.apache.pekko.annotation.InternalApi

/**
 * SWAR (SIMD Within A Register) utility class. Internal Use Only.
 * <p>
 * Copied from the Netty Project.
 * https://github.com/netty/netty/blob/d28a0fc6598b50fbe8f296831777cf4b653a475f/common/src/main/java/io/netty/util/internal/SWARUtil.java
 * </p>
 */
@InternalApi
private[util] object SWARUtil {

  private val (longBeArrayView, longBeArrayViewSupported) =
    try {
      (MethodHandles.byteArrayViewVarHandle(
          classOf[Array[Long]], java.nio.ByteOrder.BIG_ENDIAN),
        true)
    } catch {
      case _: Throwable => (null, false)
    }

  /**
   * Compiles given byte into a long pattern suitable for SWAR operations.
   */
  def compilePattern(byteToFind: Byte): Long =
    (byteToFind & 0xFFL) * 0x101010101010101L

  /**
   * Applies a compiled pattern to given word.
   * Returns a word where each byte that matches the pattern has the highest bit set.
   *
   * @param word    the word to apply the pattern to
   * @param pattern the pattern to apply
   * @return a word where each byte that matches the pattern has the highest bit set
   */
  def applyPattern(word: Long, pattern: Long): Long = {
    val input = word ^ pattern
    val tmp = (input & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL
    ~(tmp | input | 0x7F7F7F7F7F7F7F7FL)
  }

  /**
   * Returns the index of the first occurrence of byte that specificied in the pattern.
   * If no pattern is found, returns 8. Currently only supports big endian.
   *
   * @param word     the return value of {@link #applyPattern(long, long)}
   * @return the index of the first occurrence of the specified pattern in the specified word.
   * If no pattern is found, returns 8.
   */
  def getIndex(word: Long): Int =
    java.lang.Long.numberOfLeadingZeros(word) >>> 3

  /**
   * Returns the long value at the specified index in the given byte array.
   * Uses big-endian byte order. Uses a VarHandle byte array view if supported.
   * Does not range check - assumes caller has checked bounds.
   *
   * @param array the byte array to read from
   * @param index the index to read from
   * @return the long value at the specified index
   * @throws IndexOutOfBoundsException if index is out of bounds
   */
  def getLong(array: Array[Byte], index: Int): Long = {
    if (longBeArrayViewSupported) {
      longBeArrayView.get(array, index)
    } else {
      (array(index).toLong & 0xFF) << 56 |
      (array(index + 1).toLong & 0xFF) << 48 |
      (array(index + 2).toLong & 0xFF) << 40 |
      (array(index + 3).toLong & 0xFF) << 32 |
      (array(index + 4).toLong & 0xFF) << 24 |
      (array(index + 5).toLong & 0xFF) << 16 |
      (array(index + 6).toLong & 0xFF) << 8 |
      (array(index + 7).toLong & 0xFF)
    }
  }

  // Derived from code in Netty
  // https://github.com/netty/netty/blob/d28a0fc6598b50fbe8f296831777cf4b653a475f/buffer/src/main/java/io/netty/buffer/ByteBufUtil.java#L366-L408
  def arrayBytesMatch(arrayBytes: Array[Byte],
      fromIndex: Int,
      checkBytes: Array[Byte],
      bytesFromIndex: Int,
      checkLength: Int): Boolean = {
    var aIndex = fromIndex
    var bIndex = bytesFromIndex
    val longCount = checkLength >>> 3
    val byteCount = checkLength & 7
    var i = 0
    while (i < longCount) {
      if (SWARUtil.getLong(arrayBytes, aIndex) != SWARUtil.getLong(checkBytes, bIndex)) return false
      aIndex += 8
      bIndex += 8
      i += 1
    }
    i = 0
    while (i < byteCount) {
      if (arrayBytes(aIndex) != checkBytes(bIndex)) return false
      aIndex += 1
      bIndex += 1
      i += 1
    }
    true
  }

  // Derived from code in Netty
  // https://github.com/netty/netty/blob/a5343227b10456ec889a3fdc5fa4246f036a216d/buffer/src/main/java/io/netty/buffer/ByteBufUtil.java#L327-L356
  def maxSuf(arrayBytes: Array[Byte], m: Int, start: Int, isSuffix: Boolean): Long = {
    var p = 1
    var ms = -1
    var j = start
    var k = 1
    var a = 0
    var b = 0
    while (j + k < m) {
      a = arrayBytes(j + k)
      b = arrayBytes(ms + k)
      val suffix = if (isSuffix) a < b
      else a > b
      if (suffix) {
        j += k
        k = 1
        p = j - ms
      } else if (a == b) if (k != p) k += 1
      else {
        j += p
        k = 1
      }
      else {
        ms = j
        j = ms + 1
        k = 1
        p = 1
      }
    }
    (ms.toLong << 32) + p
  }
}
