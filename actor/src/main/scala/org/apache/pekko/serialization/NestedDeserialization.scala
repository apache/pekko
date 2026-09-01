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

package org.apache.pekko.serialization

import java.io.NotSerializableException

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Bounds how deeply one deserialization may nest.
 *
 * Several wire formats carry a nested payload: the enclosed message is an opaque byte
 * array that is deserialized in turn, and that payload may itself enclose another. Each
 * level is parsed on its own, so a protobuf parser's nesting limit does not bound the
 * chain - only the size of the message does. Recursion therefore tracks the nesting
 * rather than the size of the message, and a sufficiently deep chain fails with a
 * `StackOverflowError` instead of a serialization error.
 *
 * The depth is per thread because one message is deserialized synchronously on one thread.
 */
@InternalApi
private[pekko] object NestedDeserialization {

  private val depth = new ThreadLocal[Array[Int]] {
    override def initialValue(): Array[Int] = new Array[Int](1)
  }

  /**
   * Runs `body` one level deeper, failing with `NotSerializableException` - the ordinary
   * way a message that cannot be deserialized is reported - when the nesting exceeds `max`.
   */
  def atNextLevel[T](max: Int)(body: => T): T = {
    val counter = depth.get()
    counter(0) += 1
    try {
      if (counter(0) > max)
        throw new NotSerializableException(
          s"Message exceeds the maximum deserialization nesting depth of [$max]. " +
          "Configure with 'pekko.actor.serialization-max-nesting-depth'.")
      body
    } finally counter(0) -= 1
  }

  /** Current nesting depth, for testing. */
  def currentDepth: Int = depth.get()(0)
}
