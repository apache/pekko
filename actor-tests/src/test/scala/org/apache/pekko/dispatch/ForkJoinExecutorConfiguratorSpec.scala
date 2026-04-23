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

package org.apache.pekko.dispatch

import java.util.concurrent.ThreadFactory

import org.apache.pekko
import pekko.testkit.PekkoSpec
import pekko.util.JavaVersion

import com.typesafe.config.{ Config, ConfigFactory }

object ForkJoinExecutorConfiguratorSpec {
  // Keep the root config explicit so the dispatcher-config integration checks below
  // see exactly the values this spec is asserting on (and not whatever the project's
  // global test configuration happens to set). In particular, the `fj-auto-*` dispatchers
  // pin `minimum-runnable = -1` so they always exercise the JDK-aware auto policy even
  // when a build pins `pekko.actor.default-dispatcher.fork-join-executor.minimum-runnable`
  // via -D (the nightly CI does this on JDK 21+ for stability — see nightly-builds.yml).
  val config: Config = ConfigFactory.parseString("""
      |fj-auto-default-dispatcher {
      |  executor = "fork-join-executor"
      |  fork-join-executor {
      |    parallelism-min = 8
      |    parallelism-factor = 1.0
      |    parallelism-max = 64
      |    minimum-runnable = -1
      |  }
      |}
      |fj-auto-small-dispatcher {
      |  executor = "fork-join-executor"
      |  fork-join-executor {
      |    parallelism-min = 1
      |    parallelism-factor = 1.0
      |    parallelism-max = 1
      |    minimum-runnable = -1
      |  }
      |}
      |fj-explicit-zero-dispatcher {
      |  executor = "fork-join-executor"
      |  fork-join-executor {
      |    parallelism-min = 8
      |    parallelism-factor = 1.0
      |    parallelism-max = 64
      |    minimum-runnable = 0
      |  }
      |}
      |fj-explicit-seven-dispatcher {
      |  executor = "fork-join-executor"
      |  fork-join-executor {
      |    parallelism-min = 8
      |    parallelism-factor = 1.0
      |    parallelism-max = 64
      |    minimum-runnable = 7
      |  }
      |}
    """.stripMargin)
}

class ForkJoinExecutorConfiguratorSpec extends PekkoSpec(ForkJoinExecutorConfiguratorSpec.config) {

  import ForkJoinExecutorConfigurator.resolveMinimumRunnable

  "ForkJoinExecutorConfigurator.resolveMinimumRunnable" must {

    "honour explicit zero (compensation disabled)" in {
      resolveMinimumRunnable(configured = 0, parallelism = 16, jdkMajorVersion = 25) shouldBe 0
      resolveMinimumRunnable(configured = 0, parallelism = 16, jdkMajorVersion = 17) shouldBe 0
    }

    "honour explicit positive overrides verbatim" in {
      resolveMinimumRunnable(configured = 1, parallelism = 16, jdkMajorVersion = 25) shouldBe 1
      resolveMinimumRunnable(configured = 7, parallelism = 16, jdkMajorVersion = 25) shouldBe 7
      resolveMinimumRunnable(configured = 100, parallelism = 16, jdkMajorVersion = 25) shouldBe 100
    }

    "auto-resolve to 1 on JDK < 25 regardless of parallelism (preserves legacy behaviour)" in {
      // JDK 21 keeps the legacy default per reviewer guidance: only JDK 25 nightlies
      // showed the compensation-thread regression badly enough to warrant a default change.
      resolveMinimumRunnable(configured = -1, parallelism = 1, jdkMajorVersion = 17) shouldBe 1
      resolveMinimumRunnable(configured = -1, parallelism = 8, jdkMajorVersion = 17) shouldBe 1
      resolveMinimumRunnable(configured = -1, parallelism = 64, jdkMajorVersion = 11) shouldBe 1
      resolveMinimumRunnable(configured = -1, parallelism = 16, jdkMajorVersion = 21) shouldBe 1
    }

    "auto-resolve using parallelism / 2 on JDK 25+ with min cap 1 and max cap 8" in {
      resolveMinimumRunnable(configured = -1, parallelism = 1, jdkMajorVersion = 25) shouldBe 1
      resolveMinimumRunnable(configured = -1, parallelism = 2, jdkMajorVersion = 25) shouldBe 1
      resolveMinimumRunnable(configured = -1, parallelism = 4, jdkMajorVersion = 25) shouldBe 2
      resolveMinimumRunnable(configured = -1, parallelism = 8, jdkMajorVersion = 25) shouldBe 4
      resolveMinimumRunnable(configured = -1, parallelism = 16, jdkMajorVersion = 25) shouldBe 8
      resolveMinimumRunnable(configured = -1, parallelism = 64, jdkMajorVersion = 25) shouldBe 8
    }

    "produce a strictly higher value on JDK 25+ than on JDK 21 for plausible dispatcher sizes" in {
      // Directional check: the auto policy must move the needle on the JDK line that needs it
      // (and only on that line — JDK 21 stays at the legacy default per reviewer guidance).
      for (parallelism <- Seq(4, 8, 16, 32, 64)) {
        val legacy = resolveMinimumRunnable(configured = -1, parallelism, jdkMajorVersion = 21)
        val modern = resolveMinimumRunnable(configured = -1, parallelism, jdkMajorVersion = 25)
        withClue(s"parallelism=$parallelism legacy=$legacy modern=$modern: ") {
          modern should be > legacy
        }
      }
    }

    "never exceed the documented max cap of 8" in {
      for (parallelism <- 1 to 256; jdk <- Seq(21, 25, 30)) {
        resolveMinimumRunnable(configured = -1, parallelism, jdk) should be <= 8
      }
    }
  }

  "ForkJoinExecutorConfigurator wiring" must {

    // Build a factory from a real dispatcher config and return the resolved
    // minimum-runnable. This proves the config value actually reaches the
    // ForkJoinExecutorServiceFactory — guarding against the trivial regression
    // of reverting resolveMinimumRunnable to a direct `config.getInt` read.
    def resolvedMinimumRunnable(dispatcherId: String): Int = {
      // `system.dispatchers.config(id)` resolves the dispatcher's full config with
      // reference.conf defaults applied (so `virtualize`, `minimum-runnable`, etc.
      // all have values).
      val dispatcherConfig = system.dispatchers.config(dispatcherId)
      val configurator = new ForkJoinExecutorConfigurator(
        dispatcherConfig.getConfig("fork-join-executor"),
        system.dispatchers.prerequisites)
      val tf: ThreadFactory = system.dispatchers.prerequisites.threadFactory
      val factory = configurator
        .createExecutorServiceFactory(dispatcherId, tf)
        .asInstanceOf[configurator.ForkJoinExecutorServiceFactory]
      factory.minimumRunnable
    }

    "respect explicit minimum-runnable = 0" in {
      resolvedMinimumRunnable("fj-explicit-zero-dispatcher") shouldBe 0
    }

    "respect explicit minimum-runnable = 7" in {
      resolvedMinimumRunnable("fj-explicit-seven-dispatcher") shouldBe 7
    }

    "auto-scale the default (minimum-runnable not set) on JDK 25+" in {
      if (JavaVersion.majorVersion < 25) pending

      val resolved = resolvedMinimumRunnable("fj-auto-default-dispatcher")
      // The dispatcher declares parallelism-min = 8 so effective parallelism is at
      // least 8; auto = min(8, max(1, parallelism/2)) must be at least 4 and never
      // exceed the documented cap of 8.
      resolved should be >= 4
      resolved should be <= 8
    }

    "keep the legacy value of 1 on JDK < 25 when the default is left untouched" in {
      if (JavaVersion.majorVersion >= 25) pending

      resolvedMinimumRunnable("fj-auto-default-dispatcher") shouldBe 1
    }

    "never drop below 1 even for parallelism = 1 dispatchers" in {
      val resolved = resolvedMinimumRunnable("fj-auto-small-dispatcher")
      // parallelism = 1 implies parallelism/2 = 0, which the min-cap must lift to 1.
      // On JDK < 25 the legacy value of 1 is already the expected answer.
      resolved shouldBe 1
    }
  }
}
