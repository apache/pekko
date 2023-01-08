/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream

import org.apache.pekko.stream.scaladsl.{ Sink, Source }
import docs.stream.cookbook.RecipeSpec

import scala.concurrent.Future

class SinkRecipeDocSpec extends RecipeSpec {
  "Sink.foreachAsync" must {
    "processing each element asynchronously" in {
      def asyncProcessing(value: Int): Future[Unit] = Future { println(value) }(system.dispatcher)
      // #forseachAsync-processing
      // def asyncProcessing(value: Int): Future[Unit] = _

      Source(1 to 100).runWith(Sink.foreachAsync(10)(asyncProcessing))
      // #forseachAsync-processing
    }
  }
}
