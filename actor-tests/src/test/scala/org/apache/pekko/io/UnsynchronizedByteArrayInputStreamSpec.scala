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

import java.io.{ ByteArrayOutputStream, EOFException }
import java.nio.charset.StandardCharsets

import org.apache.pekko
import pekko.io.UnsynchronizedByteArrayInputStream

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UnsynchronizedByteArrayInputStreamSpec extends AnyWordSpec with Matchers {

  private def bytes(s: String): Array[Byte] = s.getBytes(StandardCharsets.UTF_8)
  private def str(b: Array[Byte]): String = new String(b, StandardCharsets.UTF_8)

  "UnsynchronizedByteArrayInputStream" must {
    "support mark and reset" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abc"))
      stream.markSupported() should ===(true)
      stream.read() should ===('a')
      stream.mark(1) // the parameter value (a readAheadLimit) is ignored as it is in ByteArrayInputStream too
      stream.read() should ===('b')
      stream.reset()
      stream.read() should ===('b')
      stream.close()
    }
    "support skip" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abc"))
      stream.skip(1) should ===(1)
      stream.read() should ===('b')
      stream.close()
    }
    "support skip with large value" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abc"))
      stream.skip(50) should ===(3) // only 3 bytes to skip
      stream.available() should ===(0)
      stream.read() should ===(-1)
      stream.skip(Long.MaxValue) should ===(0) // must not overflow
      stream.available() should ===(0)
      stream.close()
    }
    "reject negative skip" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abc"))
      an[IllegalArgumentException] should be thrownBy stream.skip(-1)
      stream.close()
    }
    "support skipNBytes" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abcdef"))
      stream.skipNBytes(0)
      stream.skipNBytes(-1) // no-op, as in InputStream
      stream.read() should ===('a')
      stream.skipNBytes(2)
      stream.read() should ===('d')
      an[EOFException] should be thrownBy stream.skipNBytes(3)
      stream.available() should ===(0)
      stream.close()
    }
    "support read into array" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abcdef"))
      val buf = new Array[Byte](4)
      stream.read(buf) should ===(4)
      str(buf) should ===("abcd")
      stream.read(buf, 1, 0) should ===(0)
      stream.read(buf, 1, 3) should ===(2)
      str(buf) should ===("aefd")
      stream.read(buf) should ===(-1)
      stream.read(buf, 0, 0) should ===(0) // zero-length read at EOF returns 0, not -1
      an[IndexOutOfBoundsException] should be thrownBy stream.read(buf, 2, 3)
      an[IndexOutOfBoundsException] should be thrownBy stream.read(buf, -1, 1)
      stream.close()
    }
    "support readAllBytes" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abcdef"))
      stream.read() should ===('a')
      str(stream.readAllBytes()) should ===("bcdef")
      stream.available() should ===(0)
      stream.readAllBytes() should ===(Array.emptyByteArray)
      stream.close()
    }
    "support readNBytes(int)" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abcdef"))
      str(stream.readNBytes(2)) should ===("ab")
      stream.readNBytes(0) should ===(Array.emptyByteArray)
      str(stream.readNBytes(Int.MaxValue)) should ===("cdef")
      stream.readNBytes(1) should ===(Array.emptyByteArray)
      an[IllegalArgumentException] should be thrownBy stream.readNBytes(-1)
      stream.close()
    }
    "support readNBytes(byte[], int, int)" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abcdef"))
      val buf = new Array[Byte](4)
      stream.readNBytes(buf, 1, 3) should ===(3)
      str(buf.slice(1, 4)) should ===("abc")
      stream.readNBytes(buf, 0, 4) should ===(3)
      str(buf.slice(0, 3)) should ===("def")
      stream.readNBytes(buf, 0, 4) should ===(0) // 0 at EOF, not -1
      an[IndexOutOfBoundsException] should be thrownBy stream.readNBytes(buf, 2, 3)
      stream.close()
    }
    "support transferTo" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abcdef"))
      stream.read() should ===('a')
      val out = new ByteArrayOutputStream()
      stream.transferTo(out) should ===(5)
      out.toString(StandardCharsets.UTF_8) should ===("bcdef")
      stream.transferTo(out) should ===(0)
      out.size() should ===(5)
      stream.close()
    }
    "support offset and length constructor" in {
      val stream = new UnsynchronizedByteArrayInputStream(bytes("abcdef"), 1, 3)
      stream.available() should ===(3)
      stream.read() should ===('b')
      stream.mark(0)
      str(stream.readAllBytes()) should ===("cd")
      stream.read() should ===(-1)
      stream.reset()
      str(stream.readNBytes(10)) should ===("cd")
      stream.close()
    }
    "clamp offset and length to the array bounds" in {
      val overLength = new UnsynchronizedByteArrayInputStream(bytes("abc"), 1, Int.MaxValue)
      overLength.available() should ===(2)
      str(overLength.readAllBytes()) should ===("bc")
      overLength.close()

      val overOffset = new UnsynchronizedByteArrayInputStream(bytes("abc"), 10, 2)
      overOffset.available() should ===(0)
      overOffset.read() should ===(-1)
      overOffset.readAllBytes() should ===(Array.emptyByteArray)
      overOffset.close()

      val empty = new UnsynchronizedByteArrayInputStream(Array.emptyByteArray, 5, 5)
      empty.available() should ===(0)
      empty.read() should ===(-1)
      empty.close()

      an[IllegalArgumentException] should be thrownBy new UnsynchronizedByteArrayInputStream(bytes("abc"), -1, 1)
      an[IllegalArgumentException] should be thrownBy new UnsynchronizedByteArrayInputStream(bytes("abc"), 0, -1)
    }
    "not copy the backing array" in {
      val arr = bytes("abc")
      val stream = new UnsynchronizedByteArrayInputStream(arr)
      arr(0) = 'z'.toByte
      stream.read() should ===('z')
      stream.close()
    }
  }
}
