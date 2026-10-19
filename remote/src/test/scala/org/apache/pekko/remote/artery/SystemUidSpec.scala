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

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ExtendedActorSystem
import pekko.remote.AddressUidExtension
import pekko.remote.RARP
import pekko.testkit.TestKit

class SystemUidSpec extends ArteryMultiNodeSpec {

  private def uidOf(sys: ActorSystem): Long = sys.asInstanceOf[ExtendedActorSystem].uid

  "The system uid with Artery" must {

    "be the uid of the transport local address" in {
      val transport = RARP(localSystem).provider.transport.asInstanceOf[ArteryTransport]
      uidOf(localSystem) should ===(transport.localAddress.uid)
      uidOf(localSystem) should ===(transport.bindAddress.uid)
      AddressUidExtension(localSystem).longAddressUid should ===(uidOf(localSystem))
    }

    "be stable" in {
      uidOf(localSystem) should ===(uidOf(localSystem))
    }

    "be different for different systems" in {
      val otherSystem = newRemoteSystem()
      uidOf(otherSystem) should !==(uidOf(localSystem))
    }

    "use the test-only-uid when defined" in {
      val otherSystem = newRemoteSystem(extraConfig = Some("pekko.test-only-uid = 4711"))
      uidOf(otherSystem) should ===(4711L)
    }
  }

  "The system uid without remoting" must {

    "be stable" in {
      val localOnly = ActorSystem("SystemUidSpec-local")
      try {
        uidOf(localOnly) should ===(uidOf(localOnly))
      } finally {
        TestKit.shutdownActorSystem(localOnly)
      }
    }
  }
}
