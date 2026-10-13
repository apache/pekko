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

package org.apache.pekko.remote.transport

import scala.annotation.nowarn
import scala.concurrent.Promise

import org.apache.pekko
import pekko.actor.Address
import pekko.remote.transport.AssociationHandle.HandleEventListener
import pekko.remote.transport.ThrottlerTransportAdapter.{ Blackhole, TokenBucket, Unthrottled }
import pekko.testkit.PekkoSpec
import pekko.util.ByteString

@nowarn("msg=deprecated")
class ThrottlerHandleSpec extends PekkoSpec {

  class TestHandle(writeResult: Boolean, onWrite: () => Unit = () => ()) extends AssociationHandle {
    var writeAttempts = 0

    override val localAddress: Address = Address("pekko", "local")
    override val remoteAddress: Address = Address("pekko", "remote")
    override val readHandlerPromise: Promise[HandleEventListener] = Promise()

    override def write(payload: ByteString): Boolean = {
      writeAttempts += 1
      onWrite()
      writeResult
    }
  }

  "A ThrottlerHandle" must {
    "preserve token bucket state when the wrapped handle does not write" in {
      val wrappedHandle = new TestHandle(writeResult = false)
      val handle = ThrottlerHandle(wrappedHandle, system.deadLetters)
      val bucket = TokenBucket(capacity = 100, tokensPerSecond = 0, nanoTimeOfLastSend = 0L, availableTokens = 100)

      handle.outboundThrottleMode.set(bucket)

      handle.write(ByteString("1234567890")) should ===(false)

      wrappedHandle.writeAttempts should ===(1)
      handle.outboundThrottleMode.get() should ===(bucket)
    }

    "consume tokens when the wrapped handle writes" in {
      val wrappedHandle = new TestHandle(writeResult = true)
      val handle = ThrottlerHandle(wrappedHandle, system.deadLetters)
      val bucket = TokenBucket(capacity = 100, tokensPerSecond = 0, nanoTimeOfLastSend = 0L, availableTokens = 100)

      handle.outboundThrottleMode.set(bucket)

      handle.write(ByteString("1234567890")) should ===(true)

      wrappedHandle.writeAttempts should ===(1)
      handle.outboundThrottleMode.get().asInstanceOf[TokenBucket].availableTokens should ===(90)
    }

    "pass through writes when unthrottled" in {
      Seq(true, false).foreach { writeResult =>
        val wrappedHandle = new TestHandle(writeResult)
        val handle = ThrottlerHandle(wrappedHandle, system.deadLetters)

        handle.write(ByteString("data")) should ===(writeResult)

        wrappedHandle.writeAttempts should ===(1)
        handle.outboundThrottleMode.get() should ===(Unthrottled)
      }
    }

    "return true without calling wrapped handle when blackholed" in {
      val wrappedHandle = new TestHandle(writeResult = true)
      val handle = ThrottlerHandle(wrappedHandle, system.deadLetters)

      handle.outboundThrottleMode.set(Blackhole)

      handle.write(ByteString("data")) should ===(true)

      wrappedHandle.writeAttempts should ===(0)
      handle.outboundThrottleMode.get() should ===(Blackhole)
    }

    "return false when token bucket has insufficient tokens" in {
      val wrappedHandle = new TestHandle(writeResult = true)
      val handle = ThrottlerHandle(wrappedHandle, system.deadLetters)
      val bucket = TokenBucket(capacity = 100, tokensPerSecond = 0, nanoTimeOfLastSend = 0L, availableTokens = 5)

      handle.outboundThrottleMode.set(bucket)

      handle.write(ByteString("1234567890")) should ===(false)

      wrappedHandle.writeAttempts should ===(0)
      handle.outboundThrottleMode.get() should ===(bucket)
    }

    "refund tokens when throttle state changes before a failed write is refunded" in {
      var handle: ThrottlerHandle = null
      val bucket = TokenBucket(capacity = 100, tokensPerSecond = 0, nanoTimeOfLastSend = 0L, availableTokens = 100)
      val wrappedHandle = new TestHandle(
        writeResult = false,
        onWrite = () => handle.outboundThrottleMode.set(bucket.copy(nanoTimeOfLastSend = 1L, availableTokens = 40)))

      handle = ThrottlerHandle(wrappedHandle, system.deadLetters)
      handle.outboundThrottleMode.set(bucket)

      handle.write(ByteString("1234567890")) should ===(false)

      wrappedHandle.writeAttempts should ===(1)
      val resultBucket = handle.outboundThrottleMode.get().asInstanceOf[TokenBucket]
      resultBucket.availableTokens should ===(50)
      resultBucket.capacity should ===(100)
      resultBucket.tokensPerSecond should ===(0.0)
    }
  }
}
