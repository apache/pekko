/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed

import scala.concurrent.duration.{ Duration, FiniteDuration }

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Extension
import pekko.actor.typed.ExtensionId
import pekko.util.JavaDurationConverters._
import pekko.util.Timeout

object TestKitSettings {

  /**
   * Reads configuration settings from `pekko.actor.testkit.typed` section.
   */
  def apply(system: ActorSystem[_]): TestKitSettings =
    Ext(system).settings

  /**
   * Reads configuration settings from given `Config` that
   * must have the same layout as the `pekko.actor.testkit.typed` section.
   */
  def apply(config: Config): TestKitSettings =
    new TestKitSettings(config)

  /**
   * Java API: Reads configuration settings from `pekko.actor.testkit.typed` section.
   */
  def create(system: ActorSystem[_]): TestKitSettings =
    apply(system)

  /**
   * Reads configuration settings from given `Config` that
   * must have the same layout as the `pekko.actor.testkit.typed` section.
   */
  def create(config: Config): TestKitSettings =
    new TestKitSettings(config)

  private object Ext extends ExtensionId[Ext] {
    override def createExtension(system: ActorSystem[_]): Ext = new Ext(system)
    def get(system: ActorSystem[_]): Ext = Ext.apply(system)
  }

  private class Ext(system: ActorSystem[_]) extends Extension {
    val settings: TestKitSettings = TestKitSettings(system.settings.config.getConfig("pekko.actor.testkit.typed"))
  }
}

final class TestKitSettings(val config: Config) {

  import pekko.util.Helpers._

  val TestTimeFactor: Double = config
    .getDouble("timefactor")
    .requiring(tf => !tf.isInfinite && tf > 0, "timefactor must be positive finite double")

  /** Dilated with `TestTimeFactor`. */
  val SingleExpectDefaultTimeout: FiniteDuration = dilated(config.getMillisDuration("single-expect-default"))

  /** Dilated with `TestTimeFactor`. */
  val ExpectNoMessageDefaultTimeout: FiniteDuration = dilated(config.getMillisDuration("expect-no-message-default"))

  /** Dilated with `TestTimeFactor`. */
  val DefaultTimeout: Timeout = Timeout(dilated(config.getMillisDuration("default-timeout")))

  /** Dilated with `TestTimeFactor`. */
  val DefaultActorSystemShutdownTimeout: FiniteDuration = dilated(config.getMillisDuration("system-shutdown-default"))

  val ThrowOnShutdownTimeout: Boolean = config.getBoolean("throw-on-shutdown-timeout")

  /** Dilated with `TestTimeFactor`. */
  val FilterLeeway: FiniteDuration = dilated(config.getMillisDuration("filter-leeway"))

  /**
   * Scala API: Scale the `duration` with the configured `TestTimeFactor`
   */
  def dilated(duration: FiniteDuration): FiniteDuration =
    Duration.fromNanos((duration.toNanos * TestTimeFactor + 0.5).toLong)

  /**
   * Java API: Scale the `duration` with the configured `TestTimeFactor`
   */
  def dilated(duration: java.time.Duration): java.time.Duration =
    dilated(duration.asScala).asJava
}
