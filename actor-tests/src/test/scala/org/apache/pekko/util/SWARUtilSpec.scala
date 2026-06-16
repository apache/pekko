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

import java.nio.ByteOrder

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class SWARUtilSpec extends AnyWordSpec with Matchers {

  val testData = Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

  "SWARUtil" must {
    "getLong" in {
      SWARUtil.getLong(testData, 0, ByteOrder.BIG_ENDIAN) should ===(0x0001020304050607L)
      SWARUtil.getLong(testData, 0, ByteOrder.LITTLE_ENDIAN) should ===(0x0706050403020100L)
      SWARUtil.getLongBEWithoutMethodHandle(testData, 0) should ===(0x0001020304050607L)
      SWARUtil.getLongLEWithoutMethodHandle(testData, 0) should ===(0x0706050403020100L)
      SWARUtil.getLong(testData, 8, ByteOrder.BIG_ENDIAN) should ===(0x08090A0B0C0D0E0FL)
      SWARUtil.getLong(testData, 8, ByteOrder.LITTLE_ENDIAN) should ===(0x0F0E0D0C0B0A0908L)
      SWARUtil.getLongBEWithoutMethodHandle(testData, 8) should ===(0x08090A0B0C0D0E0FL)
      SWARUtil.getLongLEWithoutMethodHandle(testData, 8) should ===(0x0F0E0D0C0B0A0908L)
    }
    "getInt" in {
      SWARUtil.getInt(testData, 0, ByteOrder.BIG_ENDIAN) should ===(0x00010203)
      SWARUtil.getInt(testData, 0, ByteOrder.LITTLE_ENDIAN) should ===(0x03020100)
      SWARUtil.getIntBEWithoutMethodHandle(testData, 0) should ===(0x00010203)
      SWARUtil.getIntLEWithoutMethodHandle(testData, 0) should ===(0x03020100)
      SWARUtil.getInt(testData, 4, ByteOrder.BIG_ENDIAN) should ===(0x04050607)
      SWARUtil.getInt(testData, 4, ByteOrder.LITTLE_ENDIAN) should ===(0x07060504)
      SWARUtil.getIntBEWithoutMethodHandle(testData, 4) should ===(0x04050607)
      SWARUtil.getIntLEWithoutMethodHandle(testData, 4) should ===(0x07060504)
    }
    "getShort" in {
      SWARUtil.getShort(testData, 0, ByteOrder.BIG_ENDIAN) should ===(0x0001.toShort)
      SWARUtil.getShort(testData, 0, ByteOrder.LITTLE_ENDIAN) should ===(0x0100.toShort)
      SWARUtil.getShortBEWithoutMethodHandle(testData, 0) should ===(0x0001.toShort)
      SWARUtil.getShortLEWithoutMethodHandle(testData, 0) should ===(0x0100.toShort)
      SWARUtil.getShort(testData, 2, ByteOrder.BIG_ENDIAN) should ===(0x0203.toShort)
      SWARUtil.getShort(testData, 2, ByteOrder.LITTLE_ENDIAN) should ===(0x0302.toShort)
      SWARUtil.getShortBEWithoutMethodHandle(testData, 2) should ===(0x0203.toShort)
      SWARUtil.getShortLEWithoutMethodHandle(testData, 2) should ===(0x0302.toShort)
    }
    "putInt" in {
      val arr = new Array[Byte](8)

      SWARUtil.putInt(arr, 0, 0x00010203, ByteOrder.BIG_ENDIAN)
      arr.take(4).toSeq should ===(Seq[Byte](0x00, 0x01, 0x02, 0x03))

      SWARUtil.putInt(arr, 4, 0x04050607, ByteOrder.BIG_ENDIAN)
      arr.drop(4).toSeq should ===(Seq[Byte](0x04, 0x05, 0x06, 0x07))

      SWARUtil.putInt(arr, 0, 0x00010203, ByteOrder.LITTLE_ENDIAN)
      arr.take(4).toSeq should ===(Seq[Byte](0x03, 0x02, 0x01, 0x00))

      SWARUtil.putInt(arr, 4, 0x04050607, ByteOrder.LITTLE_ENDIAN)
      arr.drop(4).toSeq should ===(Seq[Byte](0x07, 0x06, 0x05, 0x04))

      // all-zero pattern
      SWARUtil.putInt(arr, 0, 0, ByteOrder.BIG_ENDIAN)
      arr.take(4).toSeq should ===(Seq[Byte](0x00, 0x00, 0x00, 0x00))

      SWARUtil.putInt(arr, 0, 0, ByteOrder.LITTLE_ENDIAN)
      arr.take(4).toSeq should ===(Seq[Byte](0x00, 0x00, 0x00, 0x00))

      // all-ones pattern
      SWARUtil.putInt(arr, 0, -1, ByteOrder.BIG_ENDIAN)
      arr.take(4).toSeq should ===(Seq[Byte](0xFF.toByte, 0xFF.toByte, 0xFF.toByte, 0xFF.toByte))

      SWARUtil.putInt(arr, 0, -1, ByteOrder.LITTLE_ENDIAN)
      arr.take(4).toSeq should ===(Seq[Byte](0xFF.toByte, 0xFF.toByte, 0xFF.toByte, 0xFF.toByte))
    }
    "putIntBEWithoutMethodHandle" in {
      val arr = new Array[Byte](8)

      SWARUtil.putIntBEWithoutMethodHandle(arr, 0, 0x00010203)
      arr.take(4).toSeq should ===(Seq[Byte](0x00, 0x01, 0x02, 0x03))

      SWARUtil.putIntBEWithoutMethodHandle(arr, 4, 0x04050607)
      arr.drop(4).toSeq should ===(Seq[Byte](0x04, 0x05, 0x06, 0x07))

      // round-trip: putIntBEWithoutMethodHandle and getIntBEWithoutMethodHandle are inverses
      SWARUtil.putIntBEWithoutMethodHandle(arr, 0, Int.MaxValue)
      SWARUtil.getIntBEWithoutMethodHandle(arr, 0) should ===(Int.MaxValue)
      SWARUtil.putIntBEWithoutMethodHandle(arr, 0, Int.MinValue)
      SWARUtil.getIntBEWithoutMethodHandle(arr, 0) should ===(Int.MinValue)
    }
    "putIntLEWithoutMethodHandle" in {
      val arr = new Array[Byte](8)

      SWARUtil.putIntLEWithoutMethodHandle(arr, 0, 0x00010203)
      arr.take(4).toSeq should ===(Seq[Byte](0x03, 0x02, 0x01, 0x00))

      SWARUtil.putIntLEWithoutMethodHandle(arr, 4, 0x04050607)
      arr.drop(4).toSeq should ===(Seq[Byte](0x07, 0x06, 0x05, 0x04))

      // round-trip
      SWARUtil.putIntLEWithoutMethodHandle(arr, 0, Int.MaxValue)
      SWARUtil.getIntLEWithoutMethodHandle(arr, 0) should ===(Int.MaxValue)
      SWARUtil.putIntLEWithoutMethodHandle(arr, 0, Int.MinValue)
      SWARUtil.getIntLEWithoutMethodHandle(arr, 0) should ===(Int.MinValue)
    }
    "putLong" in {
      val arr = new Array[Byte](16)

      SWARUtil.putLong(arr, 0, 0x0001020304050607L, ByteOrder.BIG_ENDIAN)
      arr.take(8).toSeq should ===(Seq[Byte](0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))

      SWARUtil.putLong(arr, 8, 0x08090A0B0C0D0E0FL, ByteOrder.BIG_ENDIAN)
      arr.drop(8).toSeq should ===(Seq[Byte](0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F))

      SWARUtil.putLong(arr, 0, 0x0001020304050607L, ByteOrder.LITTLE_ENDIAN)
      arr.take(8).toSeq should ===(Seq[Byte](0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01, 0x00))

      SWARUtil.putLong(arr, 8, 0x08090A0B0C0D0E0FL, ByteOrder.LITTLE_ENDIAN)
      arr.drop(8).toSeq should ===(Seq[Byte](0x0F, 0x0E, 0x0D, 0x0C, 0x0B, 0x0A, 0x09, 0x08))

      // all-zero pattern
      SWARUtil.putLong(arr, 0, 0L, ByteOrder.BIG_ENDIAN)
      arr.take(8).toSeq should ===(Seq.fill(8)(0x00.toByte))

      SWARUtil.putLong(arr, 0, 0L, ByteOrder.LITTLE_ENDIAN)
      arr.take(8).toSeq should ===(Seq.fill(8)(0x00.toByte))

      // all-ones pattern
      SWARUtil.putLong(arr, 0, -1L, ByteOrder.BIG_ENDIAN)
      arr.take(8).toSeq should ===(Seq.fill(8)(0xFF.toByte))

      SWARUtil.putLong(arr, 0, -1L, ByteOrder.LITTLE_ENDIAN)
      arr.take(8).toSeq should ===(Seq.fill(8)(0xFF.toByte))
    }
    "putLongBEWithoutMethodHandle" in {
      val arr = new Array[Byte](16)

      SWARUtil.putLongBEWithoutMethodHandle(arr, 0, 0x0001020304050607L)
      arr.take(8).toSeq should ===(Seq[Byte](0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))

      SWARUtil.putLongBEWithoutMethodHandle(arr, 8, 0x08090A0B0C0D0E0FL)
      arr.drop(8).toSeq should ===(Seq[Byte](0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F))

      // round-trip
      SWARUtil.putLongBEWithoutMethodHandle(arr, 0, Long.MaxValue)
      SWARUtil.getLongBEWithoutMethodHandle(arr, 0) should ===(Long.MaxValue)
      SWARUtil.putLongBEWithoutMethodHandle(arr, 0, Long.MinValue)
      SWARUtil.getLongBEWithoutMethodHandle(arr, 0) should ===(Long.MinValue)
    }
    "putLongLEWithoutMethodHandle" in {
      val arr = new Array[Byte](16)

      SWARUtil.putLongLEWithoutMethodHandle(arr, 0, 0x0001020304050607L)
      arr.take(8).toSeq should ===(Seq[Byte](0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01, 0x00))

      SWARUtil.putLongLEWithoutMethodHandle(arr, 8, 0x08090A0B0C0D0E0FL)
      arr.drop(8).toSeq should ===(Seq[Byte](0x0F, 0x0E, 0x0D, 0x0C, 0x0B, 0x0A, 0x09, 0x08))

      // round-trip
      SWARUtil.putLongLEWithoutMethodHandle(arr, 0, Long.MaxValue)
      SWARUtil.getLongLEWithoutMethodHandle(arr, 0) should ===(Long.MaxValue)
      SWARUtil.putLongLEWithoutMethodHandle(arr, 0, Long.MinValue)
      SWARUtil.getLongLEWithoutMethodHandle(arr, 0) should ===(Long.MinValue)
    }
    "putInt and getInt are inverses" in {
      val arr = new Array[Byte](4)
      for (value <- Seq(0, 1, -1, Int.MaxValue, Int.MinValue, 0x12345678)) {
        SWARUtil.putInt(arr, 0, value, ByteOrder.BIG_ENDIAN)
        SWARUtil.getInt(arr, 0, ByteOrder.BIG_ENDIAN) should ===(value)
        SWARUtil.putInt(arr, 0, value, ByteOrder.LITTLE_ENDIAN)
        SWARUtil.getInt(arr, 0, ByteOrder.LITTLE_ENDIAN) should ===(value)
      }
    }
    "putLong and getLong are inverses" in {
      val arr = new Array[Byte](8)
      for (value <- Seq(0L, 1L, -1L, Long.MaxValue, Long.MinValue, 0x123456789ABCDEF0L)) {
        SWARUtil.putLong(arr, 0, value, ByteOrder.BIG_ENDIAN)
        SWARUtil.getLong(arr, 0, ByteOrder.BIG_ENDIAN) should ===(value)
        SWARUtil.putLong(arr, 0, value, ByteOrder.LITTLE_ENDIAN)
        SWARUtil.getLong(arr, 0, ByteOrder.LITTLE_ENDIAN) should ===(value)
      }
    }
    "putInt writes at correct offset" in {
      val arr = new Array[Byte](6)
      SWARUtil.putInt(arr, 1, 0x01020304, ByteOrder.BIG_ENDIAN)
      arr(0) should ===(0.toByte)
      arr(1) should ===(0x01.toByte)
      arr(2) should ===(0x02.toByte)
      arr(3) should ===(0x03.toByte)
      arr(4) should ===(0x04.toByte)
      arr(5) should ===(0.toByte)
    }
    "putLong writes at correct offset" in {
      val arr = new Array[Byte](10)
      SWARUtil.putLong(arr, 1, 0x0102030405060708L, ByteOrder.BIG_ENDIAN)
      arr(0) should ===(0.toByte)
      arr(1) should ===(0x01.toByte)
      arr(2) should ===(0x02.toByte)
      arr(3) should ===(0x03.toByte)
      arr(4) should ===(0x04.toByte)
      arr(5) should ===(0x05.toByte)
      arr(6) should ===(0x06.toByte)
      arr(7) should ===(0x07.toByte)
      arr(8) should ===(0x08.toByte)
      arr(9) should ===(0.toByte)
    }
    "putShort" in {
      val arr = new Array[Byte](4)

      SWARUtil.putShort(arr, 0, 0x0102, ByteOrder.BIG_ENDIAN)
      arr.take(2).toSeq should ===(Seq[Byte](0x01, 0x02))

      SWARUtil.putShort(arr, 2, 0x0304, ByteOrder.BIG_ENDIAN)
      arr.drop(2).toSeq should ===(Seq[Byte](0x03, 0x04))

      SWARUtil.putShort(arr, 0, 0x0102, ByteOrder.LITTLE_ENDIAN)
      arr.take(2).toSeq should ===(Seq[Byte](0x02, 0x01))

      SWARUtil.putShort(arr, 2, 0x0304, ByteOrder.LITTLE_ENDIAN)
      arr.drop(2).toSeq should ===(Seq[Byte](0x04, 0x03))
    }
    "putShortBEWithoutMethodHandle" in {
      val arr = new Array[Byte](4)

      SWARUtil.putShortBEWithoutMethodHandle(arr, 0, 0x0102)
      arr.take(2).toSeq should ===(Seq[Byte](0x01, 0x02))

      SWARUtil.putShortBEWithoutMethodHandle(arr, 2, 0x0304)
      arr.drop(2).toSeq should ===(Seq[Byte](0x03, 0x04))

      // round-trip
      SWARUtil.putShortBEWithoutMethodHandle(arr, 0, Short.MaxValue)
      SWARUtil.getShortBEWithoutMethodHandle(arr, 0) should ===(Short.MaxValue)
      SWARUtil.putShortBEWithoutMethodHandle(arr, 0, Short.MinValue)
      SWARUtil.getShortBEWithoutMethodHandle(arr, 0) should ===(Short.MinValue)
    }
    "putShortLEWithoutMethodHandle" in {
      val arr = new Array[Byte](4)

      SWARUtil.putShortLEWithoutMethodHandle(arr, 0, 0x0102)
      arr.take(2).toSeq should ===(Seq[Byte](0x02, 0x01))

      SWARUtil.putShortLEWithoutMethodHandle(arr, 2, 0x0304)
      arr.drop(2).toSeq should ===(Seq[Byte](0x04, 0x03))

      // round-trip
      SWARUtil.putShortLEWithoutMethodHandle(arr, 0, Short.MaxValue)
      SWARUtil.getShortLEWithoutMethodHandle(arr, 0) should ===(Short.MaxValue)
      SWARUtil.putShortLEWithoutMethodHandle(arr, 0, Short.MinValue)
      SWARUtil.getShortLEWithoutMethodHandle(arr, 0) should ===(Short.MinValue)
    }
    "putShort and getShort are inverses" in {
      val arr = new Array[Byte](2)
      for (value <- Seq(0.toShort, 1.toShort, (-1).toShort, Short.MaxValue, Short.MinValue, 0x0102.toShort)) {
        SWARUtil.putShort(arr, 0, value, ByteOrder.BIG_ENDIAN)
        SWARUtil.getShort(arr, 0, ByteOrder.BIG_ENDIAN) should ===(value)
        SWARUtil.putShort(arr, 0, value, ByteOrder.LITTLE_ENDIAN)
        SWARUtil.getShort(arr, 0, ByteOrder.LITTLE_ENDIAN) should ===(value)
      }
    }
    "putShort writes at correct offset" in {
      val arr = new Array[Byte](4)
      SWARUtil.putShort(arr, 1, 0x0102, ByteOrder.BIG_ENDIAN)
      arr(0) should ===(0.toByte)
      arr(1) should ===(0x01.toByte)
      arr(2) should ===(0x02.toByte)
      arr(3) should ===(0.toByte)
    }
  }

}
