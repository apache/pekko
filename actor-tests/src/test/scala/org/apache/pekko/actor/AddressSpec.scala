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

package org.apache.pekko.actor

import org.apache.pekko.testkit.PekkoSpec

class AddressSpec extends PekkoSpec {
  "Address ordering" must {

    "work correctly with multiple mixed protocols" in {
      val pekkoAddress1 = Address("pekko", "system", "host1", 1000)
      val pekkoAddress2 = Address("pekko", "system", "host2", 1000)
      val akkaAddress1 = Address("akka", "system", "host1", 1000)
      val akkaAddress2 = Address("akka", "system", "host2", 1000)

      val addresses = Seq(pekkoAddress1, pekkoAddress2, akkaAddress1, akkaAddress2)
      val sortedAddresses = addresses.sorted

      sortedAddresses should ===(Seq(akkaAddress1, akkaAddress2, pekkoAddress1, pekkoAddress2))
    }
  }
}
