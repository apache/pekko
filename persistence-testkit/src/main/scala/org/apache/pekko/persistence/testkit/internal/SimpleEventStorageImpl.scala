/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

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
