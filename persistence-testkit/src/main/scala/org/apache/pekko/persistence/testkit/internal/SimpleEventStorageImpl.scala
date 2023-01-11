/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.persistence._
import pekko.persistence.testkit.EventStorage

/**
 * INTERNAL API
 */
@InternalApi
private[testkit] class SimpleEventStorageImpl extends EventStorage {

  override type InternalRepr = PersistentRepr

  override def toInternal(repr: PersistentRepr): PersistentRepr = repr

  override def toRepr(internal: PersistentRepr): PersistentRepr = internal

}
