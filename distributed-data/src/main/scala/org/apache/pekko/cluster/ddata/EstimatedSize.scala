/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API: Rough estimate in bytes of some serialized data elements. Used
 * when creating gossip messages.
 */
@InternalApi private[pekko] object EstimatedSize {
  val LongValue = 8
  val Address = 50
  val UniqueAddress = Address + LongValue
}
