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

package org.apache.pekko.persistence.testkit.state.scaladsl

import org.apache.pekko
import pekko.persistence.CapabilityFlag
import pekko.persistence.state.DurableStateStoreSpec
import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin

import com.typesafe.config.ConfigFactory

object PersistenceTestKitDurableStateStoreTCKSpec {
  val config = PersistenceTestKitDurableStateStorePlugin.config.withFallback(ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
    """))
}

class PersistenceTestKitDurableStateStoreTCKSpec
    extends DurableStateStoreSpec(PersistenceTestKitDurableStateStoreTCKSpec.config) {
  override protected def supportsDeleteWithRevisionCheck: CapabilityFlag = CapabilityFlag.off()
  override protected def supportsSerialization: CapabilityFlag = CapabilityFlag.off()
}
