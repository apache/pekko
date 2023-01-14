/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.delivery.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class ChunkedMessage(
    serialized: ByteString,
    firstChunk: Boolean,
    lastChunk: Boolean,
    serializerId: Int,
    manifest: String) {

  override def toString: String =
    s"ChunkedMessage(${serialized.size},$firstChunk,$lastChunk,$serializerId,$manifest)"
}
