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

import java.nio.charset.StandardCharsets

import org.apache.pekko
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ArterySettingsSpec extends AnyWordSpec with Matchers {

  private def settings(tcpMagic: String): ArterySettings =
    ArterySettings(
      ConfigFactory
        .parseString(s"pekko.remote.artery.advanced.tcp-magic = $tcpMagic")
        .withFallback(ConfigFactory.load())
        .resolve()
        .getConfig("pekko.remote.artery"))

  private def magic(s: String): ByteString = ByteString(s.getBytes(StandardCharsets.UTF_8))

  "ArterySettings.TcpMagicValues" must {

    // Held as a Seq rather than a Set: a Set hashes its members, and hashing a ByteString walks
    // all of its bytes. These assertions pin the ordering and de-duplication that the Set
    // previously provided incidentally, so the collection type cannot be changed back silently.
    "default to AKKA then AKKA, in configuration order" in {
      val defaults = ArterySettings(ConfigFactory.load().getConfig("pekko.remote.artery"))
      defaults.Advanced.TcpMagicValues should ===(List(magic("AKKA"), magic("PEKK")))
      defaults.Advanced.TcpMagic should ===(magic("AKKA"))
    }

    "preserve configuration order" in {
      settings("""["AKKA", "PEKK"]""").Advanced.TcpMagicValues should ===(List(magic("AKKA"), magic("PEKK")))
    }

    "use the first configured value as the outbound magic" in {
      settings("""["AKKA", "PEKK"]""").Advanced.TcpMagic should ===(magic("AKKA"))
    }

    "drop duplicates, keeping the first occurrence" in {
      settings("""["PEKK", "AKKA", "PEKK"]""").Advanced.TcpMagicValues should ===(
        List(magic("PEKK"), magic("AKKA")))
    }

    "truncate each value to 4 bytes, and de-duplicate after truncating" in {
      settings("""["PEKKO"]""").Advanced.TcpMagicValues should ===(List(magic("PEKK")))
      settings("""["PEKKO", "PEKK"]""").Advanced.TcpMagicValues should ===(List(magic("PEKK")))
    }

    // Advanced is an object, so it is initialised lazily: the requires do not run until one of
    // its members is touched, which is why these force TcpMagicValues rather than just building
    // the settings.
    "reject a value shorter than 4 UTF-8 bytes" in {
      an[IllegalArgumentException] should be thrownBy settings("""["PEK"]""").Advanced.TcpMagicValues
    }

    "reject an empty list" in {
      an[IllegalArgumentException] should be thrownBy settings("[]").Advanced.TcpMagicValues
    }
  }
}
