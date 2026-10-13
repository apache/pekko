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

import java.lang.reflect.{ Array => JArray }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HashCodeSpec extends AnyWordSpec with Matchers {

  private def fold(seed: Int, values: Seq[Any]): Int =
    values.foldLeft(seed) { (result, value) =>
      HashCode.hash(result, value)
    }

  private def legacyHash(seed: Int, value: Any): Int = value match {
    case array: AnyRef if array.getClass.isArray =>
      var result = seed
      var index = 0
      while (index < JArray.getLength(array)) {
        result = legacyHash(result, JArray.get(array, index))
        index += 1
      }
      result
    case _ => HashCode.hash(seed, value)
  }

  "HashCode.hash" must {
    "preserve hashing semantics for every JVM array kind" in {
      val seed = HashCode.SEED
      val cases = Seq[(AnyRef, Seq[Any])](
        Array(true, false, true) -> Seq(true, false, true),
        Array('a', 'b') -> Seq('a', 'b'),
        Array[Short](1, -2) -> Seq[Short](1, -2),
        Array(1, -2, 3) -> Seq(1, -2, 3),
        Array(1L, Long.MaxValue) -> Seq(1L, Long.MaxValue),
        Array(1.25f, Float.NaN) -> Seq(1.25f, Float.NaN),
        Array(1.25d, Double.NaN) -> Seq(1.25d, Double.NaN),
        Array[Byte](1, -2) -> Seq[Byte](1, -2),
        Array[AnyRef]("a", Integer.valueOf(3)) -> Seq("a", Integer.valueOf(3)),
        Array.empty[Int] -> Seq.empty,
        Array.empty[AnyRef] -> Seq.empty)

      cases.foreach { case (array, elements) =>
        val legacyResult = legacyHash(seed, array)
        legacyResult should ===(fold(seed, elements))
        HashCode.hash(seed, array) should ===(legacyResult)
      }
    }

    "preserve the existing failure for null reference-array elements" in {
      intercept[IllegalArgumentException] {
        HashCode.hash(HashCode.SEED, Array[AnyRef](null))
      }.getMessage should include("Unexpected hash parameter: null")
    }

    "preserve recursive hashing for nested arrays" in {
      val seed = HashCode.SEED
      val afterNestedInts = fold(seed, Seq(1, 2, 3))
      val expected = HashCode.hash(afterNestedInts, "tail")

      HashCode.hash(seed, Array[AnyRef](Array(1, 2, 3), "tail")) should ===(expected)
    }
  }
}
