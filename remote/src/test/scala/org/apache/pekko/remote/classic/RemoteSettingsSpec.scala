/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.classic

import scala.annotation.nowarn
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.ConfigurationException
import pekko.remote.RemoteSettings
import pekko.testkit.PekkoSpec

@nowarn("msg=deprecated")
class RemoteSettingsSpec extends AnyWordSpec with Matchers {

  "Remote settings" must {
    "default pekko.remote.classic.log-frame-size-exceeding to off" in {
      new RemoteSettings(ConfigFactory.load()).LogFrameSizeExceeding shouldEqual None
    }

    "parse pekko.remote.classic.log-frame-size-exceeding  value as bytes" in {
      new RemoteSettings(
        ConfigFactory
          .parseString("pekko.remote.classic.log-frame-size-exceeding = 100b")
          .withFallback(ConfigFactory.load())).LogFrameSizeExceeding shouldEqual Some(100)
    }
    "fail if unknown protocol name is used" in {
      val cfg = ConfigFactory.parseString("pekko.remote.protocol-name=unknown")
        .withFallback(PekkoSpec.testConf)
      val ex = intercept[ConfigurationException] {
        new RemoteSettings(ConfigFactory.load(cfg))
      }  
      ex.getMessage shouldEqual
        """The only allowed values for pekko.remote.protocol-name are "pekko" and "akka"."""
    }
    "fail if empty accept-protocol-names is used" in {
      val cfg = ConfigFactory.parseString("pekko.remote.accept-protocol-names=[]")
        .withFallback(PekkoSpec.testConf)
      val ex = intercept[ConfigurationException] {
        new RemoteSettings(ConfigFactory.load(cfg))
      }  
      ex.getMessage should startWith("pekko.remote.accept-protocol-names setting must not be empty")
    }
    "fail if invalid accept-protocol-names value is used" in {
      val cfg = ConfigFactory.parseString("""pekko.remote.accept-protocol-names=["pekko", "unknown"]""")
        .withFallback(PekkoSpec.testConf)
      val ex = intercept[ConfigurationException] {
        new RemoteSettings(ConfigFactory.load(cfg))
      }  
      ex.getMessage shouldEqual
        """pekko.remote.accept-protocol-names is an array setting that only accepts the values "pekko" and "akka"."""
    }
  }

}
