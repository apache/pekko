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

/**
 * Records whether [[NotAllowedCaseObject]] has been initialized. A test asserts this stays
 * false while deserializing a manifest that names it, so refer to that object by its class
 * name as a string and never in code, which would initialize it.
 */
object CaseObjectInitializationProbe {
  @volatile var initialized: Boolean = false
}

/**
 * Neither bound to a Jackson serializer nor covered by `allowed-class-prefix`, so a manifest
 * naming it has to be rejected by the allow list.
 */
object NotAllowedCaseObject {
  CaseObjectInitializationProbe.initialized = true
}
