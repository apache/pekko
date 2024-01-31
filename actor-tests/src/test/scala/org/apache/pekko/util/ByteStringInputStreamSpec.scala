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

import java.io.{ ByteArrayOutputStream, InputStream, OutputStream }
import java.nio.charset.StandardCharsets

import org.apache.pekko
import pekko.util.ByteString.{ ByteString1, ByteString1C, ByteStrings }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ByteStringInputStreamSpec extends AnyWordSpec with Matchers {
  "ByteString1" must {
    "support asInputStream" in {
      ByteString1.empty.asInputStream.read() shouldEqual -1
      ByteString1.empty.asInputStream.read(Array.empty) shouldEqual -1
      toUtf8String(ByteString1.empty.asInputStream) shouldEqual ""
      toUtf8String(ByteString1.fromString("abc").asInputStream) shouldEqual "abc"
    }
  }
  "ByteString1C" must {
    "support asInputStream" in {
      ByteString1C.empty.asInputStream.read() shouldEqual -1
      ByteString1C.empty.asInputStream.read(Array.empty) shouldEqual -1
      toUtf8String(ByteString1C.empty.asInputStream) shouldEqual ""
      toUtf8String(ByteString1C.fromString("abc").asInputStream) shouldEqual "abc"
    }
  }
  "ByteStrings" must {
    "support asInputStream" in {
      val empty = ByteStrings(ByteString1.fromString(""), ByteString1.fromString(""))  
      empty.asInputStream.read() shouldEqual -1
      empty.asInputStream.read(Array.empty) shouldEqual -1
      toUtf8String(empty.asInputStream) shouldEqual ""
      val abc = ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("bc"))  
      toUtf8String(abc.asInputStream) shouldEqual "abc"
    }
  }

  private def toUtf8String(input: InputStream): String =
    new String(toByteArray(input), StandardCharsets.UTF_8)

  private def toByteArray(input: InputStream): Array[Byte] = {
    val output = new ByteArrayOutputStream
    try {
      copy(input, output)
      output.toByteArray
    } finally {
      output.close()
    }
  }

  private def copy(input: InputStream, output: OutputStream): Int = {
    val buffer = new Array[Byte](4096)
    var count = 0
    var n = input.read(buffer)
    while (n != -1) {
      output.write(buffer, 0, n)
      count += n
      n = input.read(buffer)
    }
    count
  }
}