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

package org.apache.pekko.stream.impl.fusing

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Guards the pekko-stream internal that the OpenTelemetry Java agent attaches bytecode advice to.
 *
 * `GraphInterpreter.processPush` is a private method matched by name only, and it is where the agent makes
 * the context current before a stream stage hands an element to user code. A rename or an inlining disables
 * that silently, so this spec fails loudly instead. Keep it in sync with
 * https://github.com/apache/pekko/issues/3472
 */
class InstrumentationPointsSpec extends AnyWordSpec with Matchers {

  "The pekko-stream methods instrumented by the OpenTelemetry Java agent" should {

    "include GraphInterpreter.processPush(Connection)" in {
      Class
        .forName("org.apache.pekko.stream.impl.fusing.GraphInterpreter", false, getClass.getClassLoader)
        .getDeclaredMethods
        .filter(_.getName == "processPush")
        .exists(_.getParameterTypes.toIndexedSeq.map(_.getName) == Seq(
          "org.apache.pekko.stream.impl.fusing.GraphInterpreter$Connection")) shouldBe true
    }
  }
}
