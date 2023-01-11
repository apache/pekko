/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import java.util.concurrent.atomic.AtomicLong

import org.apache.pekko.annotation.{ DoNotInherit, InternalApi }

/**
 * INTERNAL API
 * As discussed in https://github.com/akka/akka/issues/16613
 *
 * Generator of sequentially numbered actor names.
 * Pulled out from HTTP internals, most often used used by streams which materialize actors directly
 */
@DoNotInherit private[pekko] abstract class SeqActorName {
  def next(): String
  def copy(name: String): SeqActorName
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object SeqActorName {
  def apply(prefix: String) = new SeqActorNameImpl(prefix, new AtomicLong(0))
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class SeqActorNameImpl(val prefix: String, counter: AtomicLong) extends SeqActorName {
  def next(): String = prefix + '-' + counter.getAndIncrement()

  def copy(newPrefix: String): SeqActorName = new SeqActorNameImpl(newPrefix, counter)
}
