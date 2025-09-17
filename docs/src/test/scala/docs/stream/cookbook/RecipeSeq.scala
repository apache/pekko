/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.cookbook

import scala.concurrent.Future

import org.apache.pekko
import pekko.stream.scaladsl._

class RecipeSeq extends RecipeSpec {

  "Draining to a strict sequence" must {

    "not be done unsafely" in {
      val mySource = Source(1 to 3).map(_.toString)
      // #draining-to-seq-unsafe
      // Dangerous: might produce a collection with 2 billion elements!
      val f: Future[Seq[String]] = mySource.runWith(Sink.seq)
      // #draining-to-seq-unsafe
      f.futureValue should ===(Seq("1", "2", "3"))
    }

    "be done safely" in {
      val mySource = Source(1 to 3).map(_.toString)
      // #draining-to-seq-safe
      val MAX_ALLOWED_SIZE = 100

      // OK. Future will fail with a `StreamLimitReachedException`
      // if the number of incoming elements is larger than max
      val limited: Future[Seq[String]] =
        mySource.limit(MAX_ALLOWED_SIZE).runWith(Sink.seq)

      // OK. Collect up until max-th elements only, then cancel upstream
      val ignoreOverflow: Future[Seq[String]] =
        mySource.take(MAX_ALLOWED_SIZE).runWith(Sink.seq)
      // #draining-to-seq-safe
      limited.futureValue should ===(Seq("1", "2", "3"))
      ignoreOverflow.futureValue should ===(Seq("1", "2", "3"))
    }

  }

}
