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

package docs.stream.cookbook

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.util.ByteString

class RecipeParseLines extends RecipeSpec {

  "Recipe for parsing line from bytes" must {

    "work" in {
      val rawData = Source(
        List(
          ByteString("Hello World"),
          ByteString("\r"),
          ByteString("!\r"),
          ByteString("\nHello Pekko!\r\nHello Streams!"),
          ByteString("\r\n\r\n")))

      // #parse-lines
      import org.apache.pekko.stream.scaladsl.Framing
      val linesStream = rawData
        .via(Framing.delimiter(ByteString("\r\n"), maximumFrameLength = 100, allowTruncation = true))
        .map(_.utf8String)
      // #parse-lines

      Await.result(linesStream.limit(10).runWith(Sink.seq), 3.seconds) should be(
        List("Hello World\r!", "Hello Pekko!", "Hello Streams!", ""))
    }

  }

}
