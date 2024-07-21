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
import org.apache.pekko.stream.FlowShape
import org.apache.pekko.stream.scaladsl._

import scala.concurrent.Await
import scala.concurrent.duration._

class RecipeWorkerPool extends RecipeSpec {

  "Recipe for a pool of workers" must {

    "work" in {
      val myJobs = Source(List("1", "2", "3", "4", "5"))
      type Result = String

      val worker = Flow[String].map(_ + " done")

      // #worker-pool
      def balancer[In, Out](worker: Flow[In, Out, Any], workerCount: Int): Flow[In, Out, NotUsed] = {
        import GraphDSL.Implicits._

        Flow.fromGraph(GraphDSL.create() { implicit b =>
          val balancer = b.add(Balance[In](workerCount, waitForAllDownstreams = true))
          val merge = b.add(Merge[Out](workerCount))

          for (_ <- 1 to workerCount)
            // for each worker, add an edge from the balancer to the worker, then wire
            // it to the merge element
            balancer ~> worker.async ~> merge

          FlowShape(balancer.in, merge.out)
        })
      }

      val processedJobs: Source[Result, NotUsed] = myJobs.via(balancer(worker, 3))
      // #worker-pool

      Await.result(processedJobs.limit(10).runWith(Sink.seq), 3.seconds).toSet should be(
        Set("1 done", "2 done", "3 done", "4 done", "5 done"))

    }

  }

}
