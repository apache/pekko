/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.japi.journal

import com.typesafe.config.Config
import org.scalactic.source.Position
import org.scalatest.Informer

import org.apache.pekko
import pekko.persistence.CapabilityFlag
import pekko.persistence.journal.JournalPerfSpec

/**
 * JAVA API
 *
 * Java / JUnit consumable equivalent of [[pekko.persistence.journal.JournalPerfSpec]] and [[pekko.persistence.journal.JournalSpec]].
 *
 * This spec measures execution times of the basic operations that an [[pekko.persistence.PersistentActor]] provides,
 * using the provided Journal (plugin).
 *
 * It is *NOT* meant to be a comprehensive benchmark, but rather aims to help plugin developers to easily determine
 * if their plugin's performance is roughly as expected. It also validates the plugin still works under "more messages" scenarios.
 *
 * The measurements are by default printed to `System.out`, if you want to customize this please override the [[#info]] method.
 *
 * The benchmark iteration and message counts are easily customisable by overriding these methods:
 *
 * {{{
 *   @Override
 *   public long awaitDurationMillis() { return 10000; }
 *
 *   @Override
 *   public int eventsCount() { return 10 * 1000; }
 *
 *   @Override
 *   public int measurementIterations { return 10; }
 * }}}
 *
 * In case your journal plugin needs some kind of setup or teardown, override the `beforeAll` or `afterAll`
 * methods (don't forget to call `super` in your overridden methods).
 *
 * @see [[pekko.persistence.journal.JournalSpec]]
 * @see [[pekko.persistence.journal.JournalPerfSpec]]
 * @param config configures the Journal plugin to be tested
 */
class JavaJournalPerfSpec(config: Config) extends JournalPerfSpec(config) {
  override protected def info: Informer = new Informer {
    override def apply(message: String, payload: Option[Any])(implicit pos: Position): Unit =
      System.out.println(message)
  }

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = CapabilityFlag.on()

  override protected def supportsSerialization: CapabilityFlag = CapabilityFlag.on()
}
