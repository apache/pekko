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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Extra tests to run for ByteStringBuilder when building with Scala 2.13+
 */
class ByteStringBuilderScala213PlusSpec extends AnyWordSpec with Matchers {
  "ByteStringBuilder" should {
    "handle addAll with LinearSeq" in {
      val result: ByteString = ByteString.newBuilder.addAll(List[Byte]('a')).result()
      result shouldEqual ByteString("a")
    }
    "handle addAll with IndexedSeq" in {
      val result: ByteString = ByteString.newBuilder.addAll(Vector[Byte]('a')).result()
      result shouldEqual ByteString("a")
    }
    "handle addAll with ByteString" in {
      val result: ByteString = ByteString.newBuilder.addAll(ByteString("a")).result()
      result shouldEqual ByteString("a")
    }
  }
}
