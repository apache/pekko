/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed

import org.apache.pekko
import pekko.annotation.DoNotInherit

/**
 * Not for user extension.
 *
 * Instances are available from `FishingOutcomes` in the respective dsls: [[pekko.actor.testkit.typed.scaladsl.FishingOutcomes]]
 * and [[pekko.actor.testkit.typed.javadsl.FishingOutcomes]]
 */
@DoNotInherit sealed trait FishingOutcome

object FishingOutcome {

  sealed trait ContinueOutcome extends FishingOutcome
  case object Continue extends ContinueOutcome
  case object ContinueAndIgnore extends ContinueOutcome
  case object Complete extends FishingOutcome
  final case class Fail(error: String) extends FishingOutcome
}
