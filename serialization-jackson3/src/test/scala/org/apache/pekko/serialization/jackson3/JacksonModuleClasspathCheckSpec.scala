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

package org.apache.pekko.serialization.jackson3

import org.apache.pekko
import pekko.actor.ReflectiveDynamicAccess

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JacksonModuleClasspathCheckSpec extends AnyWordSpec with Matchers {

  private val otherModuleSerializer = "org.apache.pekko.serialization.jackson.JacksonSerializer"

  private def dynamicAccess(otherModuleOnClasspath: Boolean) =
    new ReflectiveDynamicAccess(getClass.getClassLoader) {
      override def classIsOnClasspath(fqcn: String): Boolean =
        if (fqcn == otherModuleSerializer) otherModuleOnClasspath else super.classIsOnClasspath(fqcn)
    }

  "The Jackson serializer classpath check" should {
    "pass when the other Jackson serializer module is not on the classpath" in {
      new ReflectiveDynamicAccess(getClass.getClassLoader).classIsOnClasspath(otherModuleSerializer) should ===(false)
      JacksonSerializer.checkOtherJacksonModuleNotOnClasspath(dynamicAccess(otherModuleOnClasspath = false))
    }

    "fail when the other Jackson serializer module is on the classpath" in {
      val e = intercept[IllegalStateException] {
        JacksonSerializer.checkOtherJacksonModuleNotOnClasspath(dynamicAccess(otherModuleOnClasspath = true))
      }
      e.getMessage should include("must not both be on the classpath")
      e.getMessage should include(otherModuleSerializer)
    }
  }
}
