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

import java.nio.charset.StandardCharsets

import org.apache.pekko
import pekko.io.UnsynchronizedByteArrayInputStream

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UnsynchronizedByteArrayInputStreamSpec extends AnyWordSpec with Matchers {
  "UnsynchronizedByteArrayInputStream" must {
    "support mark and reset" in {
      val stream = new UnsynchronizedByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))
      stream.markSupported() should ===(true)
      stream.read() should ===('a')
      stream.mark(1) // the parameter value (a readAheadLimit) is ignored as it is in ByteArrayInputStream too
      stream.read() should ===('b')
      stream.reset()
      stream.read() should ===('b')
      stream.close()
    }
    "support skip" in {
      val stream = new UnsynchronizedByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))
      stream.skip(1) should ===(1)
      stream.read() should ===('b')
      stream.close()
    }
    "support skip with large value" in {
      val stream = new UnsynchronizedByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))
      stream.skip(50) should ===(3) // only 3 bytes to skip
      stream.read() should ===(-1)
      stream.close()
    }
  }
}
