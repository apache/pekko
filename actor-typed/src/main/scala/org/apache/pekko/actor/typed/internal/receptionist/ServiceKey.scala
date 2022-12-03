/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal.receptionist

import org.apache.pekko
import pekko.actor.typed.receptionist.ServiceKey
import pekko.annotation.InternalApi

/**
 * Internal representation of [[ServiceKey]] which is needed
 * in order to use a TypedMultiMap (using keys with a type parameter does not
 * work in Scala 2.x).
 *
 * Internal API
 */
@InternalApi
private[pekko] abstract class AbstractServiceKey {
  type Protocol

  /** Type-safe down-cast */
  def asServiceKey: ServiceKey[Protocol]
}

/**
 * This is the only actual concrete service key type
 *
 * Internal API
 */
@InternalApi
final case class DefaultServiceKey[T](id: String, typeName: String) extends ServiceKey[T] {
  override def toString: String = s"ServiceKey[$typeName]($id)"
}
