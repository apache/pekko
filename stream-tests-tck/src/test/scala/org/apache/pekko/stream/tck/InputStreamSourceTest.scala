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

import com.typesafe.config.{ Config, ConfigFactory }

import org.apache.pekko
import pekko.stream.ActorAttributes
import pekko.stream.scaladsl.{ Sink, StreamConverters }
import pekko.util.ByteString

import org.reactivestreams.Publisher

class InputStreamSourceTest extends PekkoPublisherVerification[ByteString] {

  // The test's InputStream is CPU-busy (each read() returns a fresh byte without
  // blocking or yielding), so cancellation propagation through `take(elements)` can
  // be slow when `pekko.test.stream-dispatcher` is virtualized in JDK 21+/JDK 25
  // nightly runs. Extend the actor-system-terminate phase timeout so that the
  // CoordinatedShutdown phase has enough headroom for any lingering flow actors to
  // finish terminating before the outer shutdown await fires.
  override def additionalConfig: Config =
    ConfigFactory
      .parseString("pekko.coordinated-shutdown.phases.actor-system-terminate.timeout = 30 s")
      .withFallback(super.additionalConfig)

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
