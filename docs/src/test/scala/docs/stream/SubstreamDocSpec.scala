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

package docs.stream

import org.apache.pekko.stream.scaladsl.{ Sink, Source }
import org.apache.pekko.stream.SubstreamCancelStrategy
import org.apache.pekko.testkit.PekkoSpec

class SubstreamDocSpec extends PekkoSpec {

  "generate substreams by groupBy" in {
    // #groupBy1
    val source = Source(1 to 10).groupBy(3, _ % 3)
    // #groupBy1

    // #groupBy2
    Source(1 to 10).groupBy(3, _ % 3).to(Sink.ignore).run()
    // #groupBy2

    // #groupBy3
    Source(1 to 10).groupBy(3, _ % 3).mergeSubstreams.runWith(Sink.ignore)
    // #groupBy3

    // #groupBy4
    Source(1 to 10).groupBy(3, _ % 3).mergeSubstreamsWithParallelism(2).runWith(Sink.ignore)

    // concatSubstreams is equivalent to mergeSubstreamsWithParallelism(1)
    Source(1 to 10).groupBy(3, _ % 3).concatSubstreams.runWith(Sink.ignore)
    // #groupBy4
  }

  "generate substreams by splitWhen and splitAfter" in {
    // #splitWhenAfter
    Source(1 to 10).splitWhen(SubstreamCancelStrategy.drain)(_ == 3)

    Source(1 to 10).splitAfter(SubstreamCancelStrategy.drain)(_ == 3)
    // #splitWhenAfter

    // #wordCount
    val text =
      "This is the first line.\n" +
      "The second line.\n" +
      "There is also the 3rd line\n"

    val charCount = Source(text.toList)
      .splitAfter(_ == '\n')
      .filter(_ != '\n')
      .map(_ => 1)
      .reduce(_ + _)
      .to(Sink.foreach(println))
      .run()
    // #wordCount
  }

  "generate substreams by flatMapConcat and flatMapMerge" in {
    // #flatMapConcat
    Source(1 to 2).flatMapConcat(i => Source(List.fill(3)(i))).runWith(Sink.ignore)
    // #flatMapConcat

    // #flatMapMerge
    Source(1 to 2).flatMapMerge(2, i => Source(List.fill(3)(i))).runWith(Sink.ignore)
    // #flatMapMerge
  }
}
