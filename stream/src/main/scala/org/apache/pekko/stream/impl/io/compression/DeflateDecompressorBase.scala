/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl.io.compression

import java.util.zip.Inflater

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.impl.io.ByteStringParser
import pekko.stream.impl.io.ByteStringParser.{ ParseResult, ParseStep }
import pekko.util.ByteString

/** INTERNAL API */
@InternalApi private[pekko] abstract class DeflateDecompressorBase(maxBytesPerChunk: Int)
    extends ByteStringParser[ByteString] {

  abstract class DecompressorParsingLogic extends ParsingLogic {
    val inflater: Inflater
    def afterInflate: ParseStep[ByteString]
    def afterBytesRead(buffer: Array[Byte], offset: Int, length: Int): Unit
    def inflating: Inflate

    /**
     * Pre-allocated buffer to read from inflater. ByteString.fromArray below
     * will always create a copy of the read data. Keeping this fixed
     * buffer around avoids reallocating a buffer that may be too big in many
     * cases for every call of `parse`.
     */
    private[this] val buffer = new Array[Byte](maxBytesPerChunk)

    abstract class Inflate(noPostProcessing: Boolean) extends ParseStep[ByteString] {
      override def canWorkWithPartialData = true
      override def parse(reader: ByteStringParser.ByteReader): ParseResult[ByteString] = {
        inflater.setInput(reader.remainingData.toArrayUnsafe())

        val read = inflater.inflate(buffer)

        reader.skip(reader.remainingSize - inflater.getRemaining)

        if (read > 0) {
          afterBytesRead(buffer, 0, read)
          val next = if (inflater.finished()) afterInflate else this
          ParseResult(Some(ByteString.fromArray(buffer, 0, read)), next, noPostProcessing)
        } else {
          if (inflater.finished()) ParseResult(None, afterInflate, noPostProcessing)
          else throw ByteStringParser.NeedMoreData
        }
      }
    }

    override def postStop(): Unit = inflater.end()
  }
}

/** INTERNAL API */
@InternalApi private[pekko] object DeflateDecompressorBase
