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

import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.ClosedShape
import pekko.stream.scaladsl._
import pekko.stream.testkit._

class RecipeManualTrigger extends RecipeSpec {

  "Recipe for triggering a stream manually" must {
    type Trigger = Unit

    "work" in {

      val elements = Source(List("1", "2", "3", "4"))
      val pub = TestPublisher.probe[Trigger]()
      val sub = TestSubscriber.manualProbe[Message]()
      val triggerSource = Source.fromPublisher(pub)
      val sink = Sink.fromSubscriber(sub)

      // #manually-triggered-stream
      val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._
        val zip = builder.add(Zip[Message, Trigger]())
        elements      ~> zip.in0
        triggerSource ~> zip.in1
        zip.out       ~> Flow[(Message, Trigger)].map { case (msg, trigger) => msg } ~> sink
        ClosedShape
      })
      // #manually-triggered-stream

      graph.run()

      sub.expectSubscription().request(1000)
      sub.expectNoMessage(100.millis)

      pub.sendNext(())
      sub.expectNext("1")
      sub.expectNoMessage(100.millis)

      pub.sendNext(())
      pub.sendNext(())
      sub.expectNext("2")
      sub.expectNext("3")
      sub.expectNoMessage(100.millis)

      pub.sendNext(())
      sub.expectNext("4")
      sub.expectComplete()
    }

    "work with ZipWith" in {

      val elements = Source(List("1", "2", "3", "4"))
      val pub = TestPublisher.probe[Trigger]()
      val sub = TestSubscriber.manualProbe[Message]()
      val triggerSource = Source.fromPublisher(pub)
      val sink = Sink.fromSubscriber(sub)

      // #manually-triggered-stream-zipwith
      val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._
        val zip = builder.add(ZipWith((msg: Message, trigger: Trigger) => msg))

        elements      ~> zip.in0
        triggerSource ~> zip.in1
        zip.out       ~> sink
        ClosedShape
      })
      // #manually-triggered-stream-zipwith

      graph.run()

      sub.expectSubscription().request(1000)
      sub.expectNoMessage(100.millis)

      pub.sendNext(())
      sub.expectNext("1")
      sub.expectNoMessage(100.millis)

      pub.sendNext(())
      pub.sendNext(())
      sub.expectNext("2")
      sub.expectNext("3")
      sub.expectNoMessage(100.millis)

      pub.sendNext(())
      sub.expectNext("4")
      sub.expectComplete()
    }

  }

}
