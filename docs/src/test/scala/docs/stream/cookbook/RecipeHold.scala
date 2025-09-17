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
import pekko.stream.Attributes
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.testkit._

object HoldOps {
  // #hold-version-1
  import org.apache.pekko
  import pekko.stream._
  import pekko.stream.stage._
  final class HoldWithInitial[T](initial: T) extends GraphStage[FlowShape[T, T]] {
    val in = Inlet[T]("HoldWithInitial.in")
    val out = Outlet[T]("HoldWithInitial.out")

    override val shape = FlowShape.of(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var currentValue: T = initial

      setHandlers(in, out,
        new InHandler with OutHandler {
          override def onPush(): Unit = {
            currentValue = grab(in)
            pull(in)
          }

          override def onPull(): Unit = {
            push(out, currentValue)
          }
        })

      override def preStart(): Unit = {
        pull(in)
      }
    }

  }
  // #hold-version-1

  // #hold-version-2
  import org.apache.pekko
  import pekko.stream._
  import pekko.stream.stage._
  final class HoldWithWait[T] extends GraphStage[FlowShape[T, T]] {
    val in = Inlet[T]("HoldWithWait.in")
    val out = Outlet[T]("HoldWithWait.out")

    override val shape = FlowShape.of(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var currentValue: T = _
      private var waitingFirstValue = true

      setHandlers(
        in,
        out,
        new InHandler with OutHandler {
          override def onPush(): Unit = {
            currentValue = grab(in)
            if (waitingFirstValue) {
              waitingFirstValue = false
              if (isAvailable(out)) push(out, currentValue)
            }
            pull(in)
          }

          override def onPull(): Unit = {
            if (!waitingFirstValue) push(out, currentValue)
          }
        })

      override def preStart(): Unit = {
        pull(in)
      }
    }
  }
  // #hold-version-2
}

class RecipeHold extends RecipeSpec {
  import HoldOps._

  "Recipe for creating a holding element" must {

    "work for version 1" in {

      val pub = TestPublisher.probe[Int]()
      val sub = TestSubscriber.manualProbe[Int]()
      val source = Source.fromPublisher(pub)
      val sink = Sink.fromSubscriber(sub)

      source.via(new HoldWithInitial(0)).to(sink).withAttributes(Attributes.inputBuffer(1, 1)).run()

      val subscription = sub.expectSubscription()
      sub.expectNoMessage(100.millis)

      subscription.request(1)
      sub.expectNext(0)

      subscription.request(1)
      sub.expectNext(0)

      pub.sendNext(1)
      pub.sendNext(2)

      subscription.request(2)
      sub.expectNext(2)
      sub.expectNext(2)

      pub.sendComplete()
      subscription.request(1)
      sub.expectComplete()
    }

    "work for version 2" in {

      val pub = TestPublisher.probe[Int]()
      val sub = TestSubscriber.manualProbe[Int]()
      val source = Source.fromPublisher(pub)
      val sink = Sink.fromSubscriber(sub)

      source.via(new HoldWithWait).to(sink).run()

      val subscription = sub.expectSubscription()
      sub.expectNoMessage(100.millis)

      subscription.request(1)
      sub.expectNoMessage(100.millis)

      pub.sendNext(1)
      sub.expectNext(1)

      pub.sendNext(2)
      pub.sendNext(3)

      subscription.request(2)
      sub.expectNext(3)
      sub.expectNext(3)

      pub.sendComplete()
      subscription.request(1)
      sub.expectComplete()
    }

  }

}
