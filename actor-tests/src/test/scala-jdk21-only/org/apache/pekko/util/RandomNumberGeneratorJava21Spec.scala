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

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RandomNumberGeneratorJava21Spec extends AnyWordSpec with Matchers {

  "RandomNumberGenerator (Java 21+)" should {

    "support config" in {
      val config = ConfigFactory.parseString(
        """pekko.random.generator-implementation = "Xoroshiro128PlusPlus"""")
      val rng = RandomNumberGenerator.createGenerator(config)
      rng shouldBe a[Jep356RandomNumberGenerator]
      rng.nextInt(10) should (be >= 0 and be < 10)
    }

  }
}
