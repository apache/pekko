/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.cookbook

import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import scala.concurrent.Await
import scala.concurrent.duration._

class RecipeDecompress extends RecipeSpec {
  "Recipe for decompressing a Gzip stream" must {
    "work" in {
      // #decompress-gzip
      import org.apache.pekko.stream.scaladsl.Compression
      // #decompress-gzip

      val compressed =
        Source.single(ByteString.fromString("Hello World")).via(Compression.gzip)

      // #decompress-gzip
      val uncompressed = compressed.via(Compression.gunzip()).map(_.utf8String)
      // #decompress-gzip

      Await.result(uncompressed.runWith(Sink.head), 3.seconds) should be("Hello World")
    }
  }
}
