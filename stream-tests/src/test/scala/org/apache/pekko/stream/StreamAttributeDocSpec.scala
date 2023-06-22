/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.RunnableGraph
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.scaladsl.TcpAttributes
import pekko.stream.testkit.StreamSpec

class StreamAttributeDocSpec extends StreamSpec("my-stream-dispatcher = \"pekko.test.stream-dispatcher\"") {

  "Setting attributes on the runnable stream" must {

    "be shown" in {
      // no stdout from tests thank you
      val println = (_: Any) => ()

      val done = {
        // #attributes-on-stream
        val stream: RunnableGraph[Future[Done]] =
          Source(1 to 10)
            .map(_.toString)
            .toMat(Sink.foreach(println))(Keep.right)
            .withAttributes(Attributes.inputBuffer(4, 4) and
              ActorAttributes.dispatcher("my-stream-dispatcher") and
              TcpAttributes.tcpWriteBufferSize(2048))

        stream.run()
        // #attributes-on-stream
      }
      done.futureValue // block until stream is done

    }

  }

}
