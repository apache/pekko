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

package docs.stream.operators

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestProbe

object SourceOperators {

  implicit val system: ActorSystem = ???

  def fromFuture(): Unit = {
    // #sourceFromFuture

    import org.apache.pekko
    import pekko.actor.ActorSystem
    import pekko.stream.scaladsl._
    import pekko.{ Done, NotUsed }

    import scala.concurrent.Future

    val source: Source[Int, NotUsed] = Source.future(Future.successful(10))
    val sink: Sink[Int, Future[Done]] = Sink.foreach((i: Int) => println(i))

    val done: Future[Done] = source.runWith(sink) // 10
    // #sourceFromFuture
  }

  def actorRef(): Unit = {
    // #actorRef
    import org.apache.pekko
    import pekko.Done
    import pekko.actor.ActorRef
    import pekko.stream.OverflowStrategy
    import pekko.stream.CompletionStrategy
    import pekko.stream.scaladsl._

    val source: Source[Any, ActorRef] = Source.actorRef(
      completionMatcher = {
        case Done =>
          // complete stream immediately if we send it Done
          CompletionStrategy.immediately
      },
      // never fail the stream because of a message
      failureMatcher = PartialFunction.empty,
      bufferSize = 100,
      overflowStrategy = OverflowStrategy.dropHead)
    val actorRef: ActorRef = source.to(Sink.foreach(println)).run()

    actorRef ! "hello"
    actorRef ! "hello"

    // The stream completes successfully with the following message
    actorRef ! Done
    // #actorRef
  }

  def actorRefWithBackpressure(): Unit = {
    // #actorRefWithBackpressure

    import org.apache.pekko
    import pekko.actor.Status.Success
    import pekko.actor.ActorRef
    import pekko.stream.CompletionStrategy
    import pekko.stream.scaladsl._

    val probe = TestProbe()

    val source: Source[String, ActorRef] = Source.actorRefWithBackpressure[String](
      ackMessage = "ack",
      // complete when we send pekko.actor.status.Success
      completionMatcher = {
        case _: Success => CompletionStrategy.immediately
      },
      // do not fail on any message
      failureMatcher = PartialFunction.empty)
    val actorRef: ActorRef = source.to(Sink.foreach(println)).run()

    probe.send(actorRef, "hello")
    probe.expectMsg("ack")
    probe.send(actorRef, "hello")
    probe.expectMsg("ack")

    // The stream completes successfully with the following message
    actorRef ! Success(())
    // #actorRefWithBackpressure
  }

  def maybe(): Unit = {
    // #maybe
    import org.apache.pekko.stream.scaladsl._
    import scala.concurrent.Promise

    val source = Source.maybe[Int].to(Sink.foreach(elem => println(elem)))

    val promise1: Promise[Option[Int]] = source.run()
    promise1.success(Some(1)) // prints 1

    // a new Promise is returned when the stream is materialized
    val promise2 = source.run()
    promise2.success(Some(2)) // prints 2
    // #maybe
  }
}
