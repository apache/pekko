/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.protobufv3.internal.UnsafeByteOperations
import pekko.util.ByteString
import pekko.protobufv3.internal.{ ByteString => ProtoByteString }
import pekko.util.ByteString.ByteString1
import pekko.util.ByteString.ByteString1C

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object ByteStringUtils {
  def toProtoByteStringUnsafe(bytes: ByteString): ProtoByteString = {
    if (bytes.isEmpty)
      ProtoByteString.EMPTY
    else if (bytes.isInstanceOf[ByteString1C] || (bytes.isInstanceOf[ByteString1] && bytes.isCompact)) {
      UnsafeByteOperations.unsafeWrap(bytes.toArrayUnsafe())
    } else {
      // zero copy, reuse the same underlying byte arrays
      bytes.asByteBuffers.foldLeft(ProtoByteString.EMPTY) { (acc, byteBuffer) =>
        acc.concat(UnsafeByteOperations.unsafeWrap(byteBuffer))
      }
    }
  }

  def toProtoByteStringUnsafe(bytes: Array[Byte]): ProtoByteString = {
    if (bytes.isEmpty)
      ProtoByteString.EMPTY
    else {
      UnsafeByteOperations.unsafeWrap(bytes)
    }
  }
}
