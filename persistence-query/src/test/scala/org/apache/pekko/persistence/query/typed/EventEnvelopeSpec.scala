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

    "create modified copies with the with-methods" in {
      val env = envelope(filtered = false)

      val withPid = env.withPersistenceId("pid-2", "entity2", 17)
      withPid.persistenceId should ===("pid-2")
      withPid.entityType should ===("entity2")
      withPid.slice should ===(17)
      withPid.sequenceNr should ===(env.sequenceNr)

      val withEvent = env.withEvent("evt")
      withEvent.eventOption should ===(Some("evt"))
      withEvent.event should ===("evt")
      withEvent.persistenceId should ===(env.persistenceId)
      env.eventOption should ===(None)

      env.withEvent("evt").withEventOption(None).eventOption should ===(None)
      env.withEventOption(Some("evt2")).eventOption should ===(Some("evt2"))
      env.withEventOption(java.util.Optional.of("evt3")).eventOption should ===(Some("evt3"))
      env.withEvent("evt").withEventOption(java.util.Optional.empty[String]()).eventOption should ===(None)

      val withTags = env.withTags(Set("a", "b"))
      withTags.tags should ===(Set("a", "b"))
      env.tags should ===(Set.empty[String])

      val javaTags = new java.util.HashSet[String]()
      javaTags.add("c")
      val withJavaTags = env.withTags(javaTags)
      withJavaTags.tags should ===(Set("c"))
      withJavaTags.getTags() should ===(javaTags)

      val withMeta = env.withMetadata("meta")
      withMeta.eventMetadata should ===(Some("meta"))
      withMeta.getEventMetaData().get should ===("meta")
    }
  }
}
