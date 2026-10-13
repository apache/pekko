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

package org.apache.pekko.remote.transport.netty

import java.net.InetSocketAddress

import scala.annotation.nowarn

import org.apache.pekko
import pekko.testkit.PekkoSpec

@nowarn("msg=deprecated")
class NettySSLSupportSpec extends PekkoSpec {

  "NettySSLSupport" must {
    "format channel remote address for handshake failure logs" in {
      NettySSLSupport.formatRemoteAddress(new InetSocketAddress("127.0.0.1", 2552)) should ===("/127.0.0.1:2552")
    }

    "fall back when channel remote address is unavailable" in {
      NettySSLSupport.formatRemoteAddress(null) should ===("unknown")
    }

    "fall back when handshake failure cause is unavailable or has no message" in {
      NettySSLSupport.formatCause(null) should ===("unknown cause")
      NettySSLSupport.formatCause(new RuntimeException(null: String)) should ===("java.lang.RuntimeException")
    }
  }
}
