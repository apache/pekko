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

package org.apache.pekko.util

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

import org.apache.pekko
import pekko.testkit.PekkoSpec

/**
 * Covers constant pool entry kinds that the class file format gained after Java 8:
 * `CONSTANT_Dynamic` (tag 17, class file format 55.0) and `CONSTANT_Module` /
 * `CONSTANT_Package` (tags 19 and 20, format 53.0).
 *
 * Neither scalac nor javac emits these for the code in this repository today, so the test
 * takes a real class file and splices the entry into its constant pool. Entry sizes in the
 * pool are tag dependent, so failing to recognise a tag makes every later entry unreadable --
 * which is exactly the regression being guarded against.
 */
class LineNumbersConstantPoolSpec extends PekkoSpec {

  private def classFileBytes(clazz: Class[?]): Array[Byte] = {
    val resource = clazz.getName.replace('.', '/') + ".class"
    val in = clazz.getClassLoader.getResourceAsStream(resource)
    try {
      val out = new java.io.ByteArrayOutputStream()
      in.transferTo(out)
      out.toByteArray
    } finally in.close()
  }

  /** Offset of the first byte after the constant pool, and the pool's declared entry count. */
  private def poolExtent(bytes: Array[Byte]): (Int, Int) = {
    val count = ByteBuffer.wrap(bytes, 8, 2).order(ByteOrder.BIG_ENDIAN).getShort & 0xFFFF
    var offset = 10
    var index = 1
    while (index < count) {
      val tag = bytes(offset) & 0xFF
      offset += 1
      tag match {
        case 1 => // Utf8: two length bytes then the bytes themselves
          offset += 2 + (ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.BIG_ENDIAN).getShort & 0xFFFF)
        case 5 | 6 => // Long, Double: take two pool slots
          offset += 8
          index += 1
        case 7 | 8 | 16 | 19 | 20 => offset += 2
        case 15                   => offset += 3
        case _                    => offset += 4
      }
      index += 1
    }
    (offset, count)
  }

  /** Splices `entry` into the constant pool of `bytes` and bumps the pool count. */
  private def withExtraPoolEntry(bytes: Array[Byte], entry: Array[Byte]): Array[Byte] = {
    val (poolEnd, count) = poolExtent(bytes)
    val result = new Array[Byte](bytes.length + entry.length)
    System.arraycopy(bytes, 0, result, 0, poolEnd)
    System.arraycopy(entry, 0, result, poolEnd, entry.length)
    System.arraycopy(bytes, poolEnd, result, poolEnd + entry.length, bytes.length - poolEnd)
    val bumped = count + 1
    result(8) = ((bumped >> 8) & 0xFF).toByte
    result(9) = (bumped & 0xFF).toByte
    result
  }

  private def parse(bytes: Array[Byte]): LineNumbers.Result = {
    val method = LineNumbers.getClass.getDeclaredMethod("getInfo", classOf[InputStream], classOf[Option[?]])
    method.setAccessible(true)
    method.invoke(LineNumbers, new ByteArrayInputStream(bytes), None).asInstanceOf[LineNumbers.Result]
  }

  private def twoByteEntry(tag: Int): Array[Byte] = Array(tag.toByte, 0, 1)
  private def fourByteEntry(tag: Int): Array[Byte] = Array(tag.toByte, 0, 0, 0, 1)

  "LineNumbers" must {

    "read a class file whose constant pool it fully understands" in {
      // baseline: the unmodified class parses, so any failure below is down to the new entry
      parse(classFileBytes(classOf[LineNumbersConstantPoolSpec])) should not be a[LineNumbers.UnknownSourceFormat]
    }

    "read a constant pool containing a CONSTANT_Dynamic entry" in {
      val spliced = withExtraPoolEntry(classFileBytes(classOf[LineNumbersConstantPoolSpec]), fourByteEntry(17))
      parse(spliced) should not be a[LineNumbers.UnknownSourceFormat]
    }

    "read a constant pool containing CONSTANT_Module and CONSTANT_Package entries" in {
      val base = classFileBytes(classOf[LineNumbersConstantPoolSpec])
      parse(withExtraPoolEntry(base, twoByteEntry(19))) should not be a[LineNumbers.UnknownSourceFormat]
      parse(withExtraPoolEntry(base, twoByteEntry(20))) should not be a[LineNumbers.UnknownSourceFormat]
    }

    "report an unparseable class file instead of throwing" in {
      // an unrecognised tag leaves the rest of the pool unreadable; the parser must still
      // return a result rather than propagating an exception
      val spliced = withExtraPoolEntry(classFileBytes(classOf[LineNumbersConstantPoolSpec]), fourByteEntry(99))
      parse(spliced) shouldBe a[LineNumbers.UnknownSourceFormat]
    }
  }
}
