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

import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.testkit.PekkoSpec

class MigrationsScala extends PekkoSpec {

  "Examples in migration guide" must {
    "compile" in {
      lazy val dontExecuteMe = {
        // #expand-continually
        Flow[Int].expand(Iterator.continually(_))
        // #expand-continually
        // #expand-state
        Flow[Int].expand { i =>
          var state = 0
          Iterator.continually {
            state += 1
            (i, state)
          }
        }
        // #expand-state

        // #async
        val flow = Flow[Int].map(_ + 1)
        Source(1 to 10).via(flow.async)
        // #async
      }
    }
  }

}
