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

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class SWARUtilSpec extends AnyWordSpec with Matchers {

  val testData = Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

  "SWARUtil" must {
    "getLong" in {
      SWARUtil.getLong(testData, 0) should ===(0x0001020304050607L)
      SWARUtil.getLong(testData, 0, true) should ===(0x0001020304050607L)
      SWARUtil.getLong(testData, 0, false) should ===(0x0706050403020100L)
      SWARUtil.getLongBEWithoutMethodHandle(testData, 0) should ===(0x0001020304050607L)
      SWARUtil.getLongLEWithoutMethodHandle(testData, 0) should ===(0x0706050403020100L)
      SWARUtil.getLong(testData, 8) should ===(0x08090A0B0C0D0E0FL)
      SWARUtil.getLong(testData, 8, true) should ===(0x08090A0B0C0D0E0FL)
      SWARUtil.getLong(testData, 8, false) should ===(0x0F0E0D0C0B0A0908L)
      SWARUtil.getLongBEWithoutMethodHandle(testData, 8) should ===(0x08090A0B0C0D0E0FL)
      SWARUtil.getLongLEWithoutMethodHandle(testData, 8) should ===(0x0F0E0D0C0B0A0908L)
    }
    "getInt" in {
      SWARUtil.getInt(testData, 0) should ===(0x00010203)
      SWARUtil.getInt(testData, 0, true) should ===(0x00010203)
      SWARUtil.getInt(testData, 0, false) should ===(0x03020100)
      SWARUtil.getIntBEWithoutMethodHandle(testData, 0) should ===(0x00010203)
      SWARUtil.getIntLEWithoutMethodHandle(testData, 0) should ===(0x03020100)
      SWARUtil.getInt(testData, 4) should ===(0x04050607)
      SWARUtil.getInt(testData, 4, true) should ===(0x04050607)
      SWARUtil.getInt(testData, 4, false) should ===(0x07060504)
      SWARUtil.getIntBEWithoutMethodHandle(testData, 4) should ===(0x04050607)
      SWARUtil.getIntLEWithoutMethodHandle(testData, 4) should ===(0x07060504)
    }
  }

}
