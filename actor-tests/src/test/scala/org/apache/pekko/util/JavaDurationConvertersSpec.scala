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

import java.time.temporal.ChronoUnit

import org.apache.pekko
import pekko.util.JavaDurationConverters._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class JavaDurationConvertersSpec extends AnyWordSpec with Matchers {

  "JavaDurationConverters" must {

    "convert from java.time.Duration to scala.concurrent.duration.FiniteDuration" in {
      val javaDuration = java.time.Duration.ofSeconds(5, 3)
      val scalaDuration: FiniteDuration = javaDuration.asScala
      scalaDuration should ===(5.seconds + 3.nanoseconds)
    }

    "convert from scala.concurrent.duration.FiniteDuration to java.time.Duration" in {
      val scalaDuration: FiniteDuration = 5.seconds + 3.nanoseconds
      val javaDuration: java.time.Duration = scalaDuration.asJava
      javaDuration should ===(java.time.Duration.ofSeconds(5, 3))
    }

    "convert from Duration.Zero to java.time.Duration" in {
      val javaDuration: java.time.Duration = Duration.Zero.asJava
      javaDuration should ===(java.time.Duration.ZERO)
    }

    "convert infinite duration to java.time.Duration" in {
      val scalaDuration: Duration = Duration.Inf
      scalaDuration.asJava should ===(ChronoUnit.FOREVER.getDuration)
    }

    "convert minfinite duration to java.time.Duration" in {
      val scalaDuration: Duration = Duration.MinusInf
      scalaDuration.asJava should ===(ChronoUnit.FOREVER.getDuration().negated())
    }

    "convert undefined duration to java.time.Duration" in {
      val scalaDuration: Duration = Duration.Undefined
      scalaDuration.asJava should ===(ChronoUnit.FOREVER.getDuration())
    }

  }
}
