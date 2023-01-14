/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

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
