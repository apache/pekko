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

package org.apache.pekko.actor

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
class ReceiveTimeoutTypePollutionInput {
  private val messages = Array[AnyRef](Identify("id"), new Object)
  private var index = 0

  def next(): AnyRef = {
    index = (index + 1) & 1
    messages(index)
  }
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(3)
@Threads(12)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
class ReceiveTimeoutTypePollutionBenchmark {

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def isAutoReceived(message: AnyRef): Boolean =
    message.isInstanceOf[AutoReceivedMessage]

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def isNotInfluenceReceiveTimeout(message: AnyRef): Boolean =
    message.isInstanceOf[NotInfluenceReceiveTimeout]

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def isNotInfluenceReceiveTimeoutGuarded(message: AnyRef): Boolean =
    dungeon.ReceiveTimeout.isNotInfluenceReceiveTimeout(message)

  @Benchmark
  def typePolluted(input: ReceiveTimeoutTypePollutionInput): Int = {
    val message = input.next()
    val before = isNotInfluenceReceiveTimeout(message)
    val auto = isAutoReceived(message)
    val after = isNotInfluenceReceiveTimeout(message)
    (if (before) 1 else 0) | (if (auto) 2 else 0) | (if (after) 4 else 0)
  }

  @Benchmark
  def concreteTypeGuard(input: ReceiveTimeoutTypePollutionInput): Int = {
    val message = input.next()
    val before = isNotInfluenceReceiveTimeoutGuarded(message)
    val auto = isAutoReceived(message)
    val after = isNotInfluenceReceiveTimeoutGuarded(message)
    (if (before) 1 else 0) | (if (auto) 2 else 0) | (if (after) 4 else 0)
  }

  @Benchmark
  def concreteTypeGuardAndStateChange(input: ReceiveTimeoutTypePollutionInput): Int = {
    val message = input.next()
    val before = isNotInfluenceReceiveTimeoutGuarded(message)
    val auto = isAutoReceived(message)
    (if (before) 1 else 0) | (if (auto) 2 else 0)
  }
}
