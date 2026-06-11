/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import java.io.InputStream

import org.apache.pekko
import pekko.stream.ActorAttributes
import pekko.stream.scaladsl.{ Sink, StreamConverters }
import pekko.util.ByteString

import org.reactivestreams.Publisher

class InputStreamSourceTest extends PekkoPublisherVerification[ByteString] {

  def createPublisher(elements: Long): Publisher[ByteString] = {
    def inputStream = new InputStream {
      private var remaining = elements
      override def read(): Int = {
        if (remaining > 0) {
          remaining -= 1
          1
        } else -1
      }
    }

    StreamConverters
      // The TCK counts publisher elements, so emit one byte per ByteString.
      .fromInputStream(() => inputStream, chunkSize = 1)
      .withAttributes(ActorAttributes.dispatcher("pekko.test.stream-dispatcher"))
      .runWith(Sink.asPublisher(false))
  }
}
