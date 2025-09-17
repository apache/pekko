/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) since 2016 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.cookbook

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.{ Sink, Source }

class RecipeSplitter extends AnyWordSpec with BeforeAndAfterAll with Matchers with ScalaFutures {

  implicit val system: ActorSystem = ActorSystem("Test")

  "Splitter" should {
    " simple split " in {

      // #Simple-Split
      // Sample Source
      val source: Source[String, NotUsed] = Source(List("1-2-3", "2-3", "3-4"))

      val ret = source
        .map(s => s.split("-").toList)
        .mapConcat(identity)
        // Sub-streams logic
        .map(s => s.toInt)
        .runWith(Sink.seq)

      // Verify results

      ret.futureValue should be(Vector(1, 2, 3, 2, 3, 3, 4))
      // #Simple-Split

    }

    " aggregate split" in {

      // #Aggregate-Split
      // Sample Source
      val source: Source[String, NotUsed] = Source(List("1-2-3", "2-3", "3-4"))

      val result = source
        .map(s => s.split("-").toList)
        // split all messages into sub-streams
        .splitWhen(a => true)
        // now split each collection
        .mapConcat(identity)
        // Sub-streams logic
        .map(s => s.toInt)
        // aggregate each sub-stream
        .reduce((a, b) => a + b)
        // and merge back the result into the original stream
        .mergeSubstreams
        .runWith(Sink.seq);

      // Verify results
      result.futureValue should be(Vector(6, 5, 7))
      // #Aggregate-Split

    }

  }

  override protected def afterAll(): Unit = system.terminate()

}
