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

import org.apache.pekko
import pekko.annotation.InternalApi

import java.util.concurrent.ThreadLocalRandom

@InternalApi
private[pekko] trait RandomNumberGenerator {
  def nextInt(): Int
  def nextInt(n: Int): Int
  def nextLong(): Long
  def nextDouble(): Double
}

@InternalApi
private[pekko] object ThreadLocalRandomNumberGenerator extends RandomNumberGenerator {
  override def nextInt(): Int = ThreadLocalRandom.current().nextInt()
  override def nextInt(bound: Int): Int = ThreadLocalRandom.current().nextInt(bound)
  override def nextLong(): Long = ThreadLocalRandom.current().nextLong()
  override def nextDouble(): Double = ThreadLocalRandom.current().nextDouble()
}

@InternalApi
private[pekko] object RandomNumberGenerator {
  def get(): RandomNumberGenerator = ThreadLocalRandomNumberGenerator
}
