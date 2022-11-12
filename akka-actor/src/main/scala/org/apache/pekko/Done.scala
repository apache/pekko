/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

import java.io.Serializable

import org.apache.pekko.annotation.DoNotInherit

/**
 * Typically used together with `Future` to signal completion
 * but there is no actual value completed. More clearly signals intent
 * than `Unit` and is available both from Scala and Java (which `Unit` is not).
 */
@DoNotInherit sealed abstract class Done extends Serializable

case object Done extends Done {

  /**
   * Java API: the singleton instance
   */
  def getInstance(): Done = this

  /**
   * Java API: the singleton instance
   *
   * This is equivalent to [[Done.getInstance]], but can be used with static import.
   */
  def done(): Done = this
}
