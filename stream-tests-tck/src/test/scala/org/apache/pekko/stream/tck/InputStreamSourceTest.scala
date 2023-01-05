/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import java.io.InputStream

import org.reactivestreams.Publisher

import org.apache.pekko
import pekko.stream.ActorAttributes
import pekko.stream.scaladsl.{ Sink, StreamConverters }
import pekko.util.ByteString

class InputStreamSourceTest extends PekkoPublisherVerification[ByteString] {

  def createPublisher(elements: Long): Publisher[ByteString] = {
    StreamConverters
      .fromInputStream(() =>
        new InputStream {
          @volatile var num = 0
          override def read(): Int = {
            num += 1
            num
          }
        })
      .withAttributes(ActorAttributes.dispatcher("pekko.test.stream-dispatcher"))
      .take(elements)
      .runWith(Sink.asPublisher(false))
  }
}
