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
