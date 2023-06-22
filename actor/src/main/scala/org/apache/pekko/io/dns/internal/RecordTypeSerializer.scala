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
import pekko.io.dns.RecordType
import pekko.util.{ ByteIterator, ByteStringBuilder, OptionVal }

/**
 * INTERNAL API
 */
private[pekko] object RecordTypeSerializer {

  // TODO other type than ByteStringBuilder? (was used in pekko-dns)
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
