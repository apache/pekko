/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sbr

import org.apache.pekko.testkit.PekkoSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually

class LeaseMajoritySpec extends PekkoSpec() with Eventually {

  val default = ConfigFactory
    .parseString(
      """
    pekko.cluster.split-brain-resolver.lease-majority.lease-implementation = "pekko.coordination.lease.kubernetes" 
    """)
    .withFallback(ConfigFactory.load())
  val blank = ConfigFactory.parseString("""
    pekko.cluster.split-brain-resolver.lease-majority {
      lease-name = " "
    }""").withFallback(default)
  val named = ConfigFactory.parseString("""
     pekko.cluster.split-brain-resolver.lease-majority {
       lease-name = "shopping-cart-akka-sbr"
     }""").withFallback(default)

  "Split Brain Resolver Lease Majority provider" must {

    "read the configured name" in {
      new SplitBrainResolverSettings(default).leaseMajoritySettings.leaseName shouldBe None
      new SplitBrainResolverSettings(blank).leaseMajoritySettings.leaseName shouldBe None
      new SplitBrainResolverSettings(named).leaseMajoritySettings.leaseName shouldBe Some("shopping-cart-akka-sbr")
    }

    "use a safe name" in {
      new SplitBrainResolverSettings(default).leaseMajoritySettings.safeLeaseName("sysName") shouldBe "sysName-akka-sbr"
      new SplitBrainResolverSettings(blank).leaseMajoritySettings.safeLeaseName("sysName") shouldBe "sysName-akka-sbr"
      new SplitBrainResolverSettings(named).leaseMajoritySettings
        .safeLeaseName("sysName") shouldBe "shopping-cart-akka-sbr"
    }

  }
}
