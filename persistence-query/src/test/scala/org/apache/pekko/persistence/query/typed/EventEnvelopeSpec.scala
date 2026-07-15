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

package org.apache.pekko.persistence.query.typed

import org.apache.pekko

import pekko.persistence.query.NoOffset
import pekko.testkit.PekkoSpec

class EventEnvelopeSpec extends PekkoSpec {

  private def envelope(filtered: Boolean): EventEnvelope[String] =
    new EventEnvelope[String](
      NoOffset,
      persistenceId = "pid-1",
      sequenceNr = 1L,
      eventOption = None,
      timestamp = 0L,
      eventMetadata = None,
      entityType = "entity",
      slice = 0,
      filtered = filtered,
      source = "")

  "EventEnvelope" should {

    "throw a filtered-specific error from event/getEvent when the payload was filtered out" in {
      val env = envelope(filtered = true)
      intercept[IllegalStateException](env.event).getMessage should include("filtered")
      intercept[IllegalStateException](env.getEvent()).getMessage should include("filtered")
    }

    "throw a not-loaded error from event/getEvent when the payload was simply not loaded" in {
      val env = envelope(filtered = false)
      intercept[IllegalStateException](env.event).getMessage should include("not loaded")
      intercept[IllegalStateException](env.getEvent()).getMessage should include("not loaded")
    }
  }
}
