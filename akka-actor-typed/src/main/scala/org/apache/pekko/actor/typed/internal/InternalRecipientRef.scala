/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal

import org.apache.pekko
import pekko.actor.ActorRefProvider
import pekko.actor.typed.RecipientRef
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] trait InternalRecipientRef[-T] extends RecipientRef[T] {

  /**
   * Get a reference to the actor ref provider which created this ref.
   */
  def provider: ActorRefProvider

  /**
   * @return `true` if the actor is locally known to be terminated, `false` if alive or uncertain.
   */
  def isTerminated: Boolean

  def refPrefix: String = toString

}
