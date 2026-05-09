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
import java.nio.ByteOrder

import org.apache.pekko.annotation.InternalApi

/**
 * SWAR (SIMD Within A Register) utility class. Internal Use Only.
 * <p>
 * Copied from the Netty Project.
 * https://github.com/netty/netty/blob/d28a0fc6598b50fbe8f296831777cf4b653a475f/common/src/main/java/io/netty/util/internal/SWARUtil.java
 * </p>
 * <p>
 * Multi-byte reads use [[java.lang.invoke.MethodHandles#byteArrayViewVarHandle]], which allows
 * reading several bytes from a byte array as a single typed value (e.g. `short`, `int`,
 * or `long`) in one operation rather than reading and shifting each byte individually.
 * </p>
 * <p>
 * The JDK itself uses the same technique.  Since Java 17, `jdk.internal.util.ByteArray` (big
 * endian) and `jdk.internal.util.ByteArrayLittleEndian` (little endian) use
 * `MethodHandles.byteArrayViewVarHandle` for every primitive type, and those helpers back the
 * public APIs of `java.io.DataInputStream` (`readShort`, `readInt`,
 * `readLong`, etc.) and `java.util.UUID` construction from bytes.
 * </p>
 * <h3>Why this is faster than byte-by-byte shifts</h3>
 * <ul>
 *   <li><b>Single native load instruction</b> – on x86/x64 and AArch64 the HotSpot JIT intrinsifies
 *       the VarHandle access into a single `MOVZX`, `MOV`, or `LDR` instruction
 *       that reads the full value directly from memory, whereas manual byte-shift code requires
 *       multiple load-and-shift-and-or sequences that are harder for the JIT to collapse.</li>
 *   <li><b>Consolidated bounds check</b> – a single range check covers the entire multi-byte read;
 *       individual `array(i)` accesses each carry their own implicit bounds check.</li>
 *   <li><b>No alignment requirement</b> – unlike `sun.misc.Unsafe` the VarHandle variant
 *       works correctly on unaligned offsets, so callers do not need to pad or copy data to satisfy
 *       alignment constraints.</li>
 *   <li><b>SWAR arithmetic</b> – reading a full `long` with a single VarHandle call means
 *       eight bytes arrive in one register, enabling SWAR patterns that test all eight bytes in
 *       parallel (see [[applyPattern]]).</li>
 * </ul>
 * <p>
 * A runtime `try/catch` guards each VarHandle creation; if the JVM does not support the API
 * (e.g. older Android runtimes) the code falls back to explicit byte-by-byte shift implementations
 * (`getLongBEWithoutMethodHandle`, etc.) so behaviour is always correct.
 * </p>
 */
@InternalApi
private[pekko] object SWARUtil {

  private val (longBeArrayView, longBeArrayViewSupported) =
    try {
      (MethodHandles.byteArrayViewVarHandle(
          classOf[Array[Long]], java.nio.ByteOrder.BIG_ENDIAN),
        true)
    } catch {
      case _: Throwable => (null, false)
    }

  private val (longLeArrayView, longLeArrayViewSupported) =
    try {
      (MethodHandles.byteArrayViewVarHandle(
          classOf[Array[Long]], java.nio.ByteOrder.LITTLE_ENDIAN),
        true)
    } catch {
      case _: Throwable => (null, false)
    }

  private val (intBeArrayView, intBeArrayViewSupported) =
    try {
      (MethodHandles.byteArrayViewVarHandle(
          classOf[Array[Int]], java.nio.ByteOrder.BIG_ENDIAN),
        true)
    } catch {
      case _: Throwable => (null, false)
    }

  private val (intLeArrayView, intLeArrayViewSupported) =
    try {
      (MethodHandles.byteArrayViewVarHandle(
          classOf[Array[Int]], java.nio.ByteOrder.LITTLE_ENDIAN),
        true)
    } catch {
      case _: Throwable => (null, false)
    }

  private val (shortBeArrayView, shortBeArrayViewSupported) =
    try {
      (MethodHandles.byteArrayViewVarHandle(
          classOf[Array[Short]], java.nio.ByteOrder.BIG_ENDIAN),
        true)
    } catch {
      case _: Throwable => (null, false)
    }

  private val (shortLeArrayView, shortLeArrayViewSupported) =
    try {
      (MethodHandles.byteArrayViewVarHandle(
          classOf[Array[Short]], java.nio.ByteOrder.LITTLE_ENDIAN),
        true)
    } catch {
      case _: Throwable => (null, false)
    }

  /**
   * Compiles given byte into a long pattern suitable for SWAR operations.
   */
  def compilePattern(byteToFind: Byte): Long = (byteToFind & 0xFFL) * 0x101010101010101L

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
   * Returns the index of the first occurrence of byte specified in the pattern.
   * If no pattern is found, returns 8. Currently only supports big endian.
   *
   * @param word     the return value of {@link #applyPattern(long, long)}
   * @return the index of the first occurrence of the specified pattern in the specified word.
   * If no pattern is found, returns 8.
   */
  def getIndex(word: Long): Int =
    java.lang.Long.numberOfLeadingZeros(word) >>> 3

  /**
   * Returns the index of the last occurrence of a byte specified in the pattern within a word.
   * If no pattern is found, the result is -1. Currently only supports big endian.
   *
   * @param word the return value of [[applyPattern]]
   * @return the index of the last occurrence of the specified pattern in the specified word.
   */
  def getLastIndex(word: Long): Int =
    if (word == 0) -1
    else (java.lang.Long.SIZE - 1 - java.lang.Long.numberOfTrailingZeros(word)) >>> 3

  /**
   * Returns the long value at the specified index in the given byte array.
   * Uses big-endian byte order. Uses a VarHandle byte array view if supported.
   * Does not range check - assumes caller has checked bounds.
   *
   * @param array the byte array to read from
   * @param index the index to read from
   * @param byteOrder the byte order to use (big-endian or little-endian)
   * @return the long value at the specified index
   */
  def getLong(array: Array[Byte], index: Int, byteOrder: ByteOrder): Long = {
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
      if (longBeArrayViewSupported) {
        longBeArrayView.get(array, index)
      } else {
        getLongBEWithoutMethodHandle(array, index)
      }
    } else {
      if (longLeArrayViewSupported) {
        longLeArrayView.get(array, index)
      } else {
        getLongLEWithoutMethodHandle(array, index)
      }
    }
  }

  /**
   * Returns the int value at the specified index in the given byte array.
   * Uses big-endian byte order. Uses a VarHandle byte array view if supported.
   * Does not range check - assumes caller has checked bounds.
   *
   * @param array the byte array to read from
   * @param index the index to read from
   * @param byteOrder the byte order to use (big-endian or little-endian)
   * @return the int value at the specified index
   */
  def getInt(array: Array[Byte], index: Int, byteOrder: ByteOrder): Int = {
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
      if (intBeArrayViewSupported) {
        intBeArrayView.get(array, index)
      } else {
        getIntBEWithoutMethodHandle(array, index)
      }
    } else {
      if (intLeArrayViewSupported) {
        intLeArrayView.get(array, index)
      } else {
        getIntLEWithoutMethodHandle(array, index)
      }
    }
  }

  /**
   * Returns the short value at the specified index in the given byte array.
   * Uses big-endian byte order. Uses a VarHandle byte array view if supported.
   * Does not range check - assumes caller has checked bounds.
   *
   * @param array the byte array to read from
   * @param index the index to read from
   * @param byteOrder the byte order to use (big-endian or little-endian)
   * @return the short value at the specified index
   */
  def getShort(array: Array[Byte], index: Int, byteOrder: ByteOrder): Short = {
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
      if (shortBeArrayViewSupported) {
        shortBeArrayView.get(array, index).asInstanceOf[Short]
      } else {
        getShortBEWithoutMethodHandle(array, index)
      }
    } else {
      if (shortLeArrayViewSupported) {
        shortLeArrayView.get(array, index).asInstanceOf[Short]
      } else {
        getShortLEWithoutMethodHandle(array, index)
      }
    }
  }

  /**
   * Writes an int value at the specified index in the given byte array.
   * Uses a VarHandle byte array view if supported, otherwise falls back to byte-by-byte writes.
   * Does not range check - assumes caller has checked bounds.
   *
   * @param array     the byte array to write to
   * @param index     the index to write at
   * @param value     the int value to write
   * @param byteOrder the byte order to use (big-endian or little-endian)
   */
  def putInt(array: Array[Byte], index: Int, value: Int, byteOrder: ByteOrder): Unit = {
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
      if (intBeArrayViewSupported) {
        intBeArrayView.set(array, index, value)
      } else {
        putIntBEWithoutMethodHandle(array, index, value)
      }
    } else {
      if (intLeArrayViewSupported) {
        intLeArrayView.set(array, index, value)
      } else {
        putIntLEWithoutMethodHandle(array, index, value)
      }
    }
  }

  /**
   * Writes the low 16 bits of `value` at the specified index in the given byte array.
   * Uses a VarHandle byte array view if supported, otherwise falls back to byte-by-byte writes.
   * Does not range check - assumes caller has checked bounds.
   *
   * @param array     the byte array to write to
   * @param index     the index to write at
   * @param value     the value whose low 16 bits are written
   * @param byteOrder the byte order to use (big-endian or little-endian)
   */
  def putShort(array: Array[Byte], index: Int, value: Int, byteOrder: ByteOrder): Unit = {
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
      if (shortBeArrayViewSupported) {
        shortBeArrayView.set(array, index, value.toShort)
      } else {
        putShortBEWithoutMethodHandle(array, index, value)
      }
    } else {
      if (shortLeArrayViewSupported) {
        shortLeArrayView.set(array, index, value.toShort)
      } else {
        putShortLEWithoutMethodHandle(array, index, value)
      }
    }
  }

  /**
   * Writes a long value at the specified index in the given byte array.
   * Uses a VarHandle byte array view if supported, otherwise falls back to byte-by-byte writes.
   * Does not range check - assumes caller has checked bounds.
   *
   * @param array     the byte array to write to
   * @param index     the index to write at
   * @param value     the long value to write
   * @param byteOrder the byte order to use (big-endian or little-endian)
   */
  def putLong(array: Array[Byte], index: Int, value: Long, byteOrder: ByteOrder): Unit = {
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
      if (longBeArrayViewSupported) {
        longBeArrayView.set(array, index, value)
      } else {
        putLongBEWithoutMethodHandle(array, index, value)
      }
    } else {
      if (longLeArrayViewSupported) {
        longLeArrayView.set(array, index, value)
      } else {
        putLongLEWithoutMethodHandle(array, index, value)
      }
    }
  }

  // Fallback implementations for environments that do not support MethodHandles.byteArrayViewVarHandle

  private[pekko] def getLongBEWithoutMethodHandle(array: Array[Byte], index: Int): Long = {
    (array(index).toLong & 0xFF) << 56 |
    (array(index + 1).toLong & 0xFF) << 48 |
    (array(index + 2).toLong & 0xFF) << 40 |
    (array(index + 3).toLong & 0xFF) << 32 |
    (array(index + 4).toLong & 0xFF) << 24 |
    (array(index + 5).toLong & 0xFF) << 16 |
    (array(index + 6).toLong & 0xFF) << 8 |
    (array(index + 7).toLong & 0xFF)
  }

  private[pekko] def getLongLEWithoutMethodHandle(array: Array[Byte], index: Int): Long = {
    (array(index).toLong & 0xFF) |
    (array(index + 1).toLong & 0xFF) << 8 |
    (array(index + 2).toLong & 0xFF) << 16 |
    (array(index + 3).toLong & 0xFF) << 24 |
    (array(index + 4).toLong & 0xFF) << 32 |
    (array(index + 5).toLong & 0xFF) << 40 |
    (array(index + 6).toLong & 0xFF) << 48 |
    (array(index + 7).toLong & 0xFF) << 56
  }

  private[pekko] def getIntBEWithoutMethodHandle(array: Array[Byte], index: Int): Int = {
    (array(index) & 0xFF) << 24 |
    (array(index + 1) & 0xFF) << 16 |
    (array(index + 2) & 0xFF) << 8 |
    (array(index + 3) & 0xFF)
  }

  private[pekko] def getIntLEWithoutMethodHandle(array: Array[Byte], index: Int): Int = {
    (array(index) & 0xFF) |
    (array(index + 1) & 0xFF) << 8 |
    (array(index + 2) & 0xFF) << 16 |
    (array(index + 3) & 0xFF) << 24
  }

  private[pekko] def getShortBEWithoutMethodHandle(array: Array[Byte], index: Int): Short =
    ((array(index) & 0xFF) << 8 | (array(index + 1) & 0xFF)).toShort

  private[pekko] def getShortLEWithoutMethodHandle(array: Array[Byte], index: Int): Short =
    ((array(index) & 0xFF) | (array(index + 1) & 0xFF) << 8).toShort

  private[pekko] def getShortBE(b0: Byte, b1: Byte): Short = ((b0 & 0xFF) << 8 | (b1 & 0xFF)).toShort

  private[pekko] def getShortLE(b0: Byte, b1: Byte): Short = ((b0 & 0xFF) | (b1 & 0xFF) << 8).toShort

  private[pekko] def getIntBE(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int = (b0 & 0xFF) << 24 |
    (b1 & 0xFF) << 16 |
    (b2 & 0xFF) << 8 |
    (b3 & 0xFF)

  private[pekko] def getIntLE(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int = (b0 & 0xFF) |
    (b1 & 0xFF) << 8 |
    (b2 & 0xFF) << 16 |
    (b3 & 0xFF) << 24

  private[pekko] def getLongBE(
      b0: Byte,
      b1: Byte,
      b2: Byte,
      b3: Byte,
      b4: Byte,
      b5: Byte,
      b6: Byte,
      b7: Byte): Long =
    (b0.toLong & 0xFFL) << 56 |
    (b1.toLong & 0xFFL) << 48 |
    (b2.toLong & 0xFFL) << 40 |
    (b3.toLong & 0xFFL) << 32 |
    (b4.toLong & 0xFFL) << 24 |
    (b5.toLong & 0xFFL) << 16 |
    (b6.toLong & 0xFFL) << 8 |
    (b7.toLong & 0xFFL)

  private[pekko] def getLongLE(
      b0: Byte,
      b1: Byte,
      b2: Byte,
      b3: Byte,
      b4: Byte,
      b5: Byte,
      b6: Byte,
      b7: Byte): Long =
    (b0.toLong & 0xFFL) |
    (b1.toLong & 0xFFL) << 8 |
    (b2.toLong & 0xFFL) << 16 |
    (b3.toLong & 0xFFL) << 24 |
    (b4.toLong & 0xFFL) << 32 |
    (b5.toLong & 0xFFL) << 40 |
    (b6.toLong & 0xFFL) << 48 |
    (b7.toLong & 0xFFL) << 56

  private[pekko] def putIntBEWithoutMethodHandle(array: Array[Byte], index: Int, value: Int): Unit = {
    array(index) = (value >>> 24).toByte
    array(index + 1) = (value >>> 16).toByte
    array(index + 2) = (value >>> 8).toByte
    array(index + 3) = value.toByte
  }

  private[pekko] def putIntLEWithoutMethodHandle(array: Array[Byte], index: Int, value: Int): Unit = {
    array(index) = value.toByte
    array(index + 1) = (value >>> 8).toByte
    array(index + 2) = (value >>> 16).toByte
    array(index + 3) = (value >>> 24).toByte
  }

  private[pekko] def putShortBEWithoutMethodHandle(array: Array[Byte], index: Int, value: Int): Unit = {
    array(index) = (value >>> 8).toByte
    array(index + 1) = value.toByte
  }

  private[pekko] def putShortLEWithoutMethodHandle(array: Array[Byte], index: Int, value: Int): Unit = {
    array(index) = value.toByte
    array(index + 1) = (value >>> 8).toByte
  }

  private[pekko] def putLongBEWithoutMethodHandle(array: Array[Byte], index: Int, value: Long): Unit = {
    array(index) = (value >>> 56).toByte
    array(index + 1) = (value >>> 48).toByte
    array(index + 2) = (value >>> 40).toByte
    array(index + 3) = (value >>> 32).toByte
    array(index + 4) = (value >>> 24).toByte
    array(index + 5) = (value >>> 16).toByte
    array(index + 6) = (value >>> 8).toByte
    array(index + 7) = value.toByte
  }

  private[pekko] def putLongLEWithoutMethodHandle(array: Array[Byte], index: Int, value: Long): Unit = {
    array(index) = value.toByte
    array(index + 1) = (value >>> 8).toByte
    array(index + 2) = (value >>> 16).toByte
    array(index + 3) = (value >>> 24).toByte
    array(index + 4) = (value >>> 32).toByte
    array(index + 5) = (value >>> 40).toByte
    array(index + 6) = (value >>> 48).toByte
    array(index + 7) = (value >>> 56).toByte
  }

}
