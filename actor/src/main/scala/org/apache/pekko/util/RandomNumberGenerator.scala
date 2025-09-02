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

import com.typesafe.config.{ Config, ConfigFactory }

import java.lang.invoke.{ MethodHandles, MethodType }
import java.util.concurrent.ThreadLocalRandom

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] trait RandomNumberGenerator {
  def nextInt(): Int
  def nextInt(n: Int): Int
  def nextInt(origin: Int, n: Int): Int
  def nextLong(): Long
  def nextDouble(): Double
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object ThreadLocalRandomNumberGenerator extends RandomNumberGenerator {
  override def nextInt(): Int = ThreadLocalRandom.current().nextInt()
  override def nextInt(bound: Int): Int = ThreadLocalRandom.current().nextInt(bound)
  override def nextInt(origin: Int, bound: Int): Int = ThreadLocalRandom.current().nextInt(origin, bound)
  override def nextLong(): Long = ThreadLocalRandom.current().nextLong()
  override def nextDouble(): Double = ThreadLocalRandom.current().nextDouble()
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class Jep356RandomNumberGenerator(impl: String) extends RandomNumberGenerator {

  // https://openjdk.org/jeps/356

  private val rngClass = Class.forName("java.util.random.RandomGenerator")
  private val lookup = MethodHandles.publicLookup()
  private val createHandle = lookup.findStatic(rngClass, "of", MethodType.methodType(rngClass, classOf[String]))
  private val intHandle = lookup.findVirtual(rngClass, "nextInt", MethodType.methodType(classOf[Int]))
  private val intBoundHandle =
    lookup.findVirtual(rngClass, "nextInt", MethodType.methodType(classOf[Int], classOf[Int]))
  private val longHandle = lookup.findVirtual(rngClass, "nextLong", MethodType.methodType(classOf[Long]))
  private val doubleHandle = lookup.findVirtual(rngClass, "nextDouble", MethodType.methodType(classOf[Double]))
  private val rng = createHandle.invoke(impl)

  override def nextInt(): Int = intHandle.invoke(rng)
  override def nextInt(bound: Int): Int = intBoundHandle.invoke(rng, bound)
  override def nextInt(origin: Int, bound: Int): Int = {
    if (origin >= bound)
      throw new IllegalArgumentException("origin must be less than bound")
    nextInt(bound - origin) + origin
  }
  override def nextLong(): Long = longHandle.invoke(rng)
  override def nextDouble(): Double = doubleHandle.invoke(rng)
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object RandomNumberGenerator {

  /**
   * INTERNAL API. Open for testing.
   */
  def createGenerator(cfg: Config) =
    if (JavaVersion.majorVersion >= 17) {
      cfg.getString("pekko.random.generator-implementation") match {
        case "ThreadLocalRandom" => ThreadLocalRandomNumberGenerator
        case impl                => new Jep356RandomNumberGenerator(impl)
      }
    } else {
      ThreadLocalRandomNumberGenerator
    }

  private val generator = createGenerator(ConfigFactory.load())

  def get(): RandomNumberGenerator = generator
}
