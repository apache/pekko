/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.testkit

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId }
import pekko.actor.ClassicActorSystemProvider
import pekko.util.Timeout

object TestKitExtension extends ExtensionId[TestKitSettings] {
  override def get(system: ActorSystem): TestKitSettings = super.get(system)
  override def get(system: ClassicActorSystemProvider): TestKitSettings = super.get(system)
  def createExtension(system: ExtendedActorSystem): TestKitSettings = new TestKitSettings(system.settings.config)
}

class TestKitSettings(val config: Config) extends Extension {

  import pekko.util.Helpers._

  val TestTimeFactor: Double = config
    .getDouble("pekko.test.timefactor")
    .requiring(tf => !tf.isInfinite && tf > 0, "pekko.test.timefactor must be positive finite double")
  val SingleExpectDefaultTimeout: FiniteDuration = config.getMillisDuration("pekko.test.single-expect-default")
  val ExpectNoMessageDefaultTimeout: FiniteDuration = config.getMillisDuration("pekko.test.expect-no-message-default")
  val TestEventFilterLeeway: FiniteDuration = config.getMillisDuration("pekko.test.filter-leeway")
  val DefaultTimeout: Timeout = Timeout(config.getMillisDuration("pekko.test.default-timeout"))
}
