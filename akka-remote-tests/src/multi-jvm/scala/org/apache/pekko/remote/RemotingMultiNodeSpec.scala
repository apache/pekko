/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote

import com.typesafe.config.ConfigFactory
import org.scalatest.Suite

import org.apache.pekko
import pekko.remote.artery.ArterySpecSupport
import pekko.remote.testkit.{ MultiNodeConfig, MultiNodeSpec, STMultiNodeSpec }
import pekko.testkit.{ DefaultTimeout, ImplicitSender }

object RemotingMultiNodeSpec {

  def commonConfig =
    ConfigFactory.parseString(s"""
        pekko.actor.warn-about-java-serializer-usage = off
      """).withFallback(ArterySpecSupport.tlsConfig) // TLS only used if transport=tls-tcp

}

abstract class RemotingMultiNodeSpec(config: MultiNodeConfig)
    extends MultiNodeSpec(config)
    with Suite
    with STMultiNodeSpec
    with ImplicitSender
    with DefaultTimeout { self: MultiNodeSpec => }
