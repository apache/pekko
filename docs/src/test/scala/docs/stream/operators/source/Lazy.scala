/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.source

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

object Lazy {

  implicit val system: ActorSystem = ???

  def createExpensiveSource(): Source[String, NotUsed] = ???

  def notReallyThatLazy(): Unit = {
    // #not-a-good-example
    val source = Source.lazySource { () =>
      println("Creating the actual source")
      createExpensiveSource()
    }

    val queue = source.runWith(Sink.queue())

    // ... time passes ...
    // at some point in time we pull the first time
    // but the source creation may already have been triggered
    queue.pull()
    // #not-a-good-example
  }

  class IteratorLikeThing {
    def thereAreMore: Boolean = ???
    def extractNext: String = ???
  }
  def safeMutableSource(): Unit = {
    // #one-per-materialization
    val stream = Source
      .lazySource { () =>
        val iteratorLike = new IteratorLikeThing
        Source.unfold(iteratorLike) { iteratorLike =>
          if (iteratorLike.thereAreMore) Some((iteratorLike, iteratorLike.extractNext))
          else None
        }
      }
      .to(Sink.foreach(println))

    // each of the three materializations will have their own instance of IteratorLikeThing
    stream.run()
    stream.run()
    stream.run()
    // #one-per-materialization
  }
}
