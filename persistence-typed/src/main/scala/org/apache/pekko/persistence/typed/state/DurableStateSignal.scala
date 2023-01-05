/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state

import org.apache.pekko
import pekko.actor.typed.Signal
import pekko.annotation.DoNotInherit

/**
 * Supertype for all `DurableStateBehavior` specific signals
 *
 * Not for user extension
 */
@DoNotInherit
sealed trait DurableStateSignal extends Signal

@DoNotInherit sealed abstract class RecoveryCompleted extends DurableStateSignal
case object RecoveryCompleted extends RecoveryCompleted {
  def instance: RecoveryCompleted = this
}

final case class RecoveryFailed(failure: Throwable) extends DurableStateSignal {

  /**
   * Java API
   */
  def getFailure(): Throwable = failure
}
