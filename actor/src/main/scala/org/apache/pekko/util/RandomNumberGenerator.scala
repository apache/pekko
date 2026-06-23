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

import com.typesafe.config.Config

import java.util.concurrent.ThreadLocalRandom
import java.util.random.RandomGenerator

/**
 * INTERNAL API: A provider for RandomGenerators
 */
private[pekko] trait RandomGeneratorProvider {

  /**
   * @return a RandomGenerator - for ThreadLocalRandom (the default), this RandomGenerator instance
   * is based on the tread. Otherwise, it is thread-safe RandomGenerator that can be used by any thread
   */
  def get(): RandomGenerator
}

/**
 * INTERNAL API: A provider for RandomGenerators based on ThreadLocalRandom
 */
private[pekko] object ThreadLocalRandomProvider extends RandomGeneratorProvider {
  override def get(): RandomGenerator = ThreadLocalRandom.current()
}

/**
 * INTERNAL API: Create a RandomGenerator based on https://openjdk.org/jeps/356
 */
@InternalApi
private[pekko] object Jep356RandomNumberGenerator {
  def apply(impl: String): RandomGeneratorProvider = {
    val rg = RandomGenerator.of(impl)
    if (rg == null) {
      throw new IllegalArgumentException(s"RandomGenerator implementation '$impl' not found")
    }
    new RandomGeneratorProvider {
      override def get(): RandomGenerator = rg
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object RandomNumberGenerator {
  private val GeneratorImplementationKey = "pekko.random.generator-implementation"

  /**
   * INTERNAL API: Get a RandomGeneratorProvider based on the config.
   */
  def getGeneratorProvider(config: Config): RandomGeneratorProvider = {
    if (config.hasPath(GeneratorImplementationKey)) {
      config.getString(GeneratorImplementationKey) match {
        case "ThreadLocalRandom" => ThreadLocalRandomProvider
        case impl                => Jep356RandomNumberGenerator(impl)
      }
    } else {
      ThreadLocalRandomProvider
    }
  }
}
