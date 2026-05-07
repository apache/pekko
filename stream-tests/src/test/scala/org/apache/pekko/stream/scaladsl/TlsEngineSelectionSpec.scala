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

package org.apache.pekko.stream.scaladsl

import org.apache.pekko.testkit.PekkoSpec

class TlsEngineSelectionSpec extends PekkoSpec {

  "TLS engine selection" should {

    "ignore blank system properties and use the configured value" in {
      TLS.configuredEngineName(Some(""), TLS.GraphStageEngineName) shouldBe TLS.GraphStageEngineName
      TLS.configuredEngineName(Some("  "), TLS.LegacyActorEngineName) shouldBe TLS.LegacyActorEngineName
    }

    "let non-blank system properties override the configured value" in {
      TLS.configuredEngineName(Some(TLS.GraphStageEngineName), TLS.LegacyActorEngineName) shouldBe
      TLS.GraphStageEngineName
    }
  }
}
