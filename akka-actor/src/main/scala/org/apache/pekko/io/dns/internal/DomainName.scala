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
    while (true) {
      val length = it.getByte
      if (length == 0) {
        val r = ret.result()
        return r
      }

      if (ret.nonEmpty)
        ret.append('.')

      if ((length & 0xC0) == 0xC0) {
        val offset = ((length.toShort & 0x3F) << 8) | (it.getByte.toShort & 0x00FF)
        return ret.result() + parse(msg.iterator.drop(offset), msg)
      }

      ret.appendAll(it.clone().take(length).map(_.toChar))
      it.drop(length)
    }
    throw new RuntimeException(s"Unable to parse domain name from msg: $msg")
  }
}
