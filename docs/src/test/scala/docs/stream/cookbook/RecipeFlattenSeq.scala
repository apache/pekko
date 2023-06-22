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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{ Sink, Source }

import scala.concurrent.Await
import scala.concurrent.duration._

class RecipeFlattenSeq extends RecipeSpec {

  "Recipe for flattening a stream of seqs" must {

    "work" in {

      val someDataSource = Source(List(List("1"), List("2"), List("3", "4", "5"), List("6", "7")))

      // #flattening-seqs
      val myData: Source[List[Message], NotUsed] = someDataSource
      val flattened: Source[Message, NotUsed] = myData.mapConcat(identity)
      // #flattening-seqs

      Await.result(flattened.limit(8).runWith(Sink.seq), 3.seconds) should be(List("1", "2", "3", "4", "5", "6", "7"))

    }

  }

}
