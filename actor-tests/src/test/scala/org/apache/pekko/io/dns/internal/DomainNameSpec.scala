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

package org.apache.pekko.io.dns.internal

import org.apache.pekko
import pekko.util.ByteString

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DomainNameSpec extends AnyWordSpec with Matchers {

  private def label(s: String): Seq[Byte] = s.length.toByte +: s.getBytes("US-ASCII").toSeq

  private def pointer(offset: Int): Seq[Byte] =
    Seq((0xC0 | (offset >> 8)).toByte, (offset & 0xFF).toByte)

  "DomainName.parse" should {
    "parse a plain name" in {
      val bytes = ByteString((label("www") ++ label("example") ++ label("com") :+ 0.toByte): _*)
      DomainName.parse(bytes.iterator, bytes) should be("www.example.com")
    }

    "follow a backwards compression pointer" in {
      // offset 0: example.com   offset 13: www + pointer to offset 0
      val bytes = ByteString((label("example") ++ label("com") :+ 0.toByte) ++ label("www") ++ pointer(0): _*)
      DomainName.parse(bytes.iterator.drop(13), bytes) should be("www.example.com")
    }

    "reject a pointer that points at itself" in {
      // offset 0: www, offset 4: pointer to offset 4
      val bytes = ByteString(label("www") ++ pointer(4): _*)
      an[IllegalArgumentException] should be thrownBy DomainName.parse(bytes.iterator, bytes)
    }

    "reject a pointer cycle" in {
      // offset 0: a + pointer to offset 4, offset 4: b + pointer to offset 0
      val bytes = ByteString(label("a") ++ pointer(4) ++ label("b") ++ pointer(0): _*)
      an[IllegalArgumentException] should be thrownBy DomainName.parse(bytes.iterator, bytes)
    }

    "reject reserved label types" in {
      val bytes = ByteString(0x40.toByte, 'a'.toByte, 0.toByte)
      an[IllegalArgumentException] should be thrownBy DomainName.parse(bytes.iterator, bytes)
    }

    "fail rather than loop on a truncated name" in {
      val bytes = ByteString(label("www"): _*)
      a[NoSuchElementException] should be thrownBy DomainName.parse(bytes.iterator, bytes)
    }
  }
}
