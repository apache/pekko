/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io.dns.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.{ ByteIterator, ByteString, ByteStringBuilder }

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object DomainName {

  /**
   * RFC 1035 section 2.3.4 limits a name to 255 octets, so a well-formed name can never
   * contain more than a handful of compression pointers. Bounding the number of pointers
   * followed guarantees that `parse` terminates even for a response whose pointers form a
   * cycle, which would otherwise recurse until the stack overflows.
   */
  private val MaxPointerHops = 16
  private val MaxNameLength = 255

  def length(name: String): Short = {
    (name.length + 2).toShort
  }

  def write(it: ByteStringBuilder, name: String): Unit = {
    for (label <- name.split('.')) {
      it.putByte(label.length.toByte)
      for (c <- label) {
        it.putByte(c.toByte)
      }
    }
    it.putByte(0)
  }

  def parse(it: ByteIterator, msg: ByteString): String = {
    val ret = new StringBuilder()
    var current = it
    var hops = 0
    while (true) {
      val length = current.getByte
      if (length == 0) {
        return ret.result()
      }

      if ((length & 0xC0) == 0xC0) {
        // compression pointer: the remainder of the name lives at `offset` in the message
        hops += 1
        if (hops > MaxPointerHops)
          throw new IllegalArgumentException(
            s"Unable to parse domain name: more than $MaxPointerHops compression pointers, probable pointer loop")
        val offset = ((length & 0x3F) << 8) | (current.getByte & 0xFF)
        current = msg.iterator.drop(offset)
      } else if ((length & 0xC0) != 0) {
        // 0x40 and 0x80 label types are reserved (RFC 1035 section 4.1.4)
        throw new IllegalArgumentException(
          s"Unable to parse domain name: unsupported label type [${(length & 0xC0) >> 6}]")
      } else {
        if (ret.nonEmpty)
          ret.append('.')
        ret.appendAll(current.clone().take(length).map(_.toChar))
        current.drop(length)
        if (ret.length > MaxNameLength)
          throw new IllegalArgumentException(
            s"Unable to parse domain name: name longer than $MaxNameLength characters")
      }
    }
    throw new IllegalStateException(s"Unable to parse domain name from msg: $msg")
  }
}
