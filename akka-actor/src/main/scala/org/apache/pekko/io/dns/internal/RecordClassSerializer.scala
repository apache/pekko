/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io.dns.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.io.dns.RecordClass
import pekko.util.{ ByteIterator, ByteStringBuilder }

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object RecordClassSerializer {

  def parse(it: ByteIterator): RecordClass = {
    it.getShort match {
      case 1       => RecordClass.IN
      case 2       => RecordClass.CS
      case 3       => RecordClass.CH
      case 255     => RecordClass.WILDCARD
      case unknown => throw new RuntimeException(s"Unexpected record class $unknown")
    }
  }

  def write(out: ByteStringBuilder, c: RecordClass): Unit = {
    out.putShort(c.code)
  }
}
