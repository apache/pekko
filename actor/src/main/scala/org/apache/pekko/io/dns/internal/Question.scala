/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io.dns.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.io.dns.{ RecordClass, RecordType }
import pekko.util.{ ByteIterator, ByteString, ByteStringBuilder }

/**
 * INTE
 */
@InternalApi
private[pekko] final case class Question(name: String, qType: RecordType, qClass: RecordClass) {
  def write(out: ByteStringBuilder): Unit = {
    DomainName.write(out, name)
    RecordTypeSerializer.write(out, qType)
    RecordClassSerializer.write(out, qClass)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object Question {
  def parse(it: ByteIterator, msg: ByteString): Question = {
    val name = DomainName.parse(it, msg)
    val qType = RecordTypeSerializer.parse(it)
    val qClass = RecordClassSerializer.parse(it)
    Question(name, qType, qClass)
  }
}
