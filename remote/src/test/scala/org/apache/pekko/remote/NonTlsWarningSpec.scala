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

package org.apache.pekko.remote

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.remote.artery.ArterySettings
import pekko.testkit.PekkoSpec

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

class NonTlsWarningSpec extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  private var systems: List[ActorSystem] = Nil

  private def createSystem(name: String, config: com.typesafe.config.Config): ActorSystem = {
    val sys = ActorSystem(name, config)
    systems = systems :+ sys
    sys
  }

  override def afterAll(): Unit = {
    systems.foreach { sys =>
      Await.result(sys.terminate(), 10.seconds)
    }
    super.afterAll()
  }

  "RemoteActorRefProvider" must {

    "detect Artery TCP transport as non-TLS" in {
      val config = ConfigFactory
        .parseString("""
          pekko.remote.artery.transport = tcp
        """)
        .withFallback(PekkoSpec.testConf)
        .withFallback(ConfigFactory.load())

      val system = createSystem("artery-tcp-test", config)
      val settings = new RemoteSettings(system.settings.config)
      settings.Artery.Enabled should be(true)
      settings.Artery.Transport should be(ArterySettings.Tcp)
    }

    "detect Artery Aeron UDP transport as non-TLS" in {
      val config = ConfigFactory
        .parseString("""
          pekko.remote.artery.transport = aeron-udp
        """)
        .withFallback(PekkoSpec.testConf)
        .withFallback(ConfigFactory.load())

      val system = createSystem("artery-aeron-test", config)
      val settings = new RemoteSettings(system.settings.config)
      settings.Artery.Enabled should be(true)
      settings.Artery.Transport should be(ArterySettings.AeronUpd)
    }

    "detect Classic Netty transport as non-TLS by default" in {
      val config = ConfigFactory
        .parseString("""
          pekko.remote.artery.enabled = off
        """)
        .withFallback(PekkoSpec.testConf)
        .withFallback(ConfigFactory.load())

      val system = createSystem("classic-netty-test", config)
      val settings = new RemoteSettings(system.settings.config)
      settings.Artery.Enabled should be(false)
      settings.Transports should not be empty
      // Default classic transport (pekko.remote.classic.netty.tcp) has no enable-ssl key
      settings.Transports.foreach {
        case (_, _, transportConfig) =>
          val hasSsl = transportConfig.hasPath("enable-ssl") && transportConfig.getBoolean("enable-ssl")
          hasSsl should be(false)
      }
    }

    "not warn for Artery TLS-TCP transport" in {
      val config = ConfigFactory
        .parseString("""
          pekko.remote.artery.transport = tls-tcp
        """)
        .withFallback(PekkoSpec.testConf)
        .withFallback(ConfigFactory.load())

      val system = createSystem("artery-tls-tcp-test", config)
      val settings = new RemoteSettings(system.settings.config)
      settings.Artery.Enabled should be(true)
      settings.Artery.Transport should be(ArterySettings.TlsTcp)
    }

    "detect Classic Netty SSL transport as TLS-enabled" in {
      val config = ConfigFactory
        .parseString("""
          pekko.remote.artery.enabled = off
          pekko.remote.classic.enabled-transports = ["pekko.remote.classic.netty.ssl"]
        """)
        .withFallback(PekkoSpec.testConf)
        .withFallback(ConfigFactory.load())

      val system = createSystem("classic-netty-ssl-test", config)
      val settings = new RemoteSettings(system.settings.config)
      settings.Artery.Enabled should be(false)
      settings.Transports should not be empty
      settings.Transports.exists {
        case (_, _, transportConfig) =>
          transportConfig.hasPath("enable-ssl") && transportConfig.getBoolean("enable-ssl")
      } should be(true)
    }

  }

}
