/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io.dns.internal

import org.apache.pekko
import pekko.io.dns.RecordType
import pekko.util.{ ByteIterator, ByteStringBuilder, OptionVal }

/**
 * INTERNAL API
 */
private[pekko] object RecordTypeSerializer {

  // TODO other type than ByteStringBuilder? (was used in akka-dns)
  def write(out: ByteStringBuilder, value: RecordType): Unit = {
    out.putShort(value.code)
  }

  def parse(it: ByteIterator): RecordType = {
    val id = it.getShort
    RecordType(id) match {
      case OptionVal.Some(t) => t
      case _                 => throw new IllegalArgumentException(s"Illegal id [$id] for DnsRecordType")
    }
  }

}
