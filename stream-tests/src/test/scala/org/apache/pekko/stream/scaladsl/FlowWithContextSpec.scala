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

package org.apache.pekko.stream.scaladsl

import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSink

class FlowWithContextSpec extends StreamSpec {

  "A FlowWithContext" must {

    "get created from Flow.asFlowWithContext" in {
      val flow = Flow[Message].map { case m => m.copy(data = m.data + "z") }
      val flowWithContext = flow.asFlowWithContext((m: Message, o: Long) => Message(m.data, o)) { m =>
        m.offset
      }

      val msg = Message("a", 1L)
      Source(Vector(msg))
        .asSourceWithContext(_.offset)
        .via(flowWithContext)
        .asSource
        .runWith(TestSink[(Message, Long)]())
        .request(1)
        .expectNext((Message("az", 1L), 1L))
        .expectComplete()
    }

    "be able to map materialized value via FlowWithContext.mapMaterializedValue" in {
      val materializedValue = "MatedValue"
      val mapMaterializedValueFlow = FlowWithContext[Message, Long].mapMaterializedValue(_ => materializedValue)

      val msg = Message("a", 1L)
      val (matValue, probe) = Source(Vector(msg))
        .mapMaterializedValue(_ => 42)
        .asSourceWithContext(_.offset)
        .viaMat(mapMaterializedValueFlow)(Keep.both)
        .toMat(TestSink[(Message, Long)]())(Keep.both)
        .run()
      matValue shouldBe (42 -> materializedValue)
      probe.request(1).expectNext((Message("a", 1L), 1L)).expectComplete()
    }

    "be able to map error via FlowWithContext.mapError" in {
      val ex = new RuntimeException("ex") with NoStackTrace
      val boom = new Exception("BOOM!") with NoStackTrace
      val mapErrorFlow = FlowWithContext[Message, Long]
        .map {
          case m @ Message(_, offset) => if (offset == 3) throw ex else m
        }
        .mapError { case _: Throwable => boom }

      Source(1L to 4L)
        .map { offset =>
          Message("a", offset)
        }
        .asSourceWithContext(_.offset)
        .via(mapErrorFlow)
        .runWith(TestSink[(Message, Long)]())
        .request(3)
        .expectNext((Message("a", 1L), 1L))
        .expectNext((Message("a", 2L), 2L))
        .expectError(boom)
    }

    "pass through contexts using additional filtering operators" in {
      val flow = FlowWithContext[Any, String]
        .collectType[Int]
        .collectWhile { case value if value < 3 => value * 10 }

      SourceWithContext
        .fromTuples(Source(Vector((1: Any, "one"), (2: Any, "two"), ("three": Any, "three"), (3: Any, "three-int"))))
        .via(flow)
        .runWith(TestSink[(Int, String)]())
        .request(3)
        .expectNext((10, "one"), (20, "two"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source(Vector((0, "zero"), (1, "one"), (2, "two"), (3, "three"))))
        .via(FlowWithContext[Int, String].mapOption(value => if (value == 0) None else Some(value * 10)))
        .runWith(TestSink[(Int, String)]())
        .request(3)
        .expectNext((10, "one"), (20, "two"), (30, "three"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source(Vector((1: Any, "one"), (2: Any, "two"), (3: Any, "three"))))
        .via(FlowWithContext[Any, String].collectFirst { case value: Int if value > 1 => value * 10 })
        .runWith(TestSink[(Int, String)]())
        .request(1)
        .expectNext((20, "two"))
        .expectComplete()

      val forComprehensionFlow =
        for {
          value <- FlowWithContext[Int, String]
          if value % 2 == 1
        } yield value * 10

      SourceWithContext
        .fromTuples(Source(Vector((1, "one"), (2, "two"), (3, "three"))))
        .via(forComprehensionFlow)
        .runWith(TestSink[(Int, String)]())
        .request(2)
        .expectNext((10, "one"), (30, "three"))
        .expectComplete()
    }

    "pass through contexts using truncating operators" in {
      val flow = FlowWithContext[Int, String]
        .drop(1)
        .dropRepeated()
        .dropWhile(_ < 2)
        .takeUntil(_ == 3)
        .takeWithin(1.day)
        .take(2)
        .limit(2)
        .limitWeighted(2)(_ => 1)

      SourceWithContext
        .fromTuples(Source(Vector((0, "zero"), (1, "one"), (1, "one-duplicate"), (2, "two"), (3, "three"),
          (4, "four"))))
        .via(flow)
        .runWith(TestSink[(Int, String)]())
        .request(2)
        .expectNext((2, "two"), (3, "three"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source(Vector((1, "one"), (2, "two"), (3, "three"))))
        .via(FlowWithContext[Int, String].takeWhile(_ < 2, inclusive = true))
        .runWith(TestSink[(Int, String)]())
        .request(2)
        .expectNext((1, "one"), (2, "two"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source(Vector((1, "one"), (3, "three"), (4, "four"))))
        .via(
          FlowWithContext[Int, String]
            .dropRepeated((left, right) => left % 2 == right % 2)
            .takeWhile(_ < 3))
        .runWith(TestSink[(Int, String)]())
        .request(2)
        .expectNext((1, "one"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source.single((1, "one")).initialDelay(50.millis))
        .via(FlowWithContext[Int, String].dropWithin(10.millis).takeWithin(1.day))
        .runWith(TestSink[(Int, String)]())
        .request(1)
        .expectNext((1, "one"))
        .expectComplete()
    }

    "pass through all data when using alsoTo" in {
      // alsoTo feeds an asynchronous side Sink, which may still be draining when the
      // main stream completes. Poll until it has observed every element instead of
      // asserting once (the single assertion raced under JDK 25 scheduling).
      val received = new ConcurrentLinkedQueue[String]()
      Source(Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L)))
        .asSourceWithContext(_.offset)
        .via(
          FlowWithContext.fromTuples(Flow.fromFunction[(Message, Long), (String, Long)] { case (data, offset) =>
            (data.data.toLowerCase, offset)
          })
            .alsoTo(Sink.foreach(string => received.add(string)))
        )
        .toMat(TestSink[(String, Long)]())(Keep.right)
        .run()
        .request(4)
        .expectNext(("a", 1L))
        .expectNext(("b", 2L))
        .expectNext(("d", 3L))
        .expectNext(("c", 4L))
        .expectComplete()
      awaitAssert(received.asScala.toList should contain theSameElementsInOrderAs List("a", "b", "d", "c"), 10.seconds)
    }

    "pass through all data when using alsoToContext" in {
      val received = new ConcurrentLinkedQueue[Long]()
      Source(Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L)))
        .asSourceWithContext(_.offset)
        .via(
          FlowWithContext.fromTuples(Flow.fromFunction[(Message, Long), (String, Long)] { case (data, offset) =>
            (data.data.toLowerCase, offset)
          })
            .alsoToContext(Sink.foreach(offset => received.add(offset)))
        )
        .toMat(TestSink[(String, Long)]())(Keep.right)
        .run()
        .request(4)
        .expectNext(("a", 1L))
        .expectNext(("b", 2L))
        .expectNext(("d", 3L))
        .expectNext(("c", 4L))
        .expectComplete()
      awaitAssert(received.asScala.toList should contain theSameElementsInOrderAs List(1L, 2L, 3L, 4L), 10.seconds)
    }

    "pass through all data when using wireTap" in {
      val received = new ConcurrentLinkedQueue[String]()
      Source(Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L)))
        .asSourceWithContext(_.offset)
        .via(
          FlowWithContext.fromTuples(Flow.fromFunction[(Message, Long), (String, Long)] { case (data, offset) =>
            (data.data.toLowerCase, offset)
          }).wireTap(Sink.foreach(string => received.add(string)))
        )
        .toMat(TestSink[(String, Long)]())(Keep.right)
        .run()
        .request(4)
        .expectNext(("a", 1L))
        .expectNext(("b", 2L))
        .expectNext(("d", 3L))
        .expectNext(("c", 4L))
        .expectComplete()
      awaitAssert(received.asScala.toList should contain atLeastOneElementOf List("a", "b", "d", "c"), 10.seconds)
    }

    "pass through all data when using wireTapContext" in {
      val received = new ConcurrentLinkedQueue[Long]()
      Source(Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L)))
        .asSourceWithContext(_.offset)
        .via(
          FlowWithContext.fromTuples(Flow.fromFunction[(Message, Long), (String, Long)] { case (data, offset) =>
            (data.data.toLowerCase, offset)
          }).wireTapContext(Sink.foreach(offset => received.add(offset)))
        )
        .toMat(TestSink[(String, Long)]())(Keep.right)
        .run()
        .request(4)
        .expectNext(("a", 1L))
        .expectNext(("b", 2L))
        .expectNext(("d", 3L))
        .expectNext(("c", 4L))
        .expectComplete()
      awaitAssert(received.asScala.toList should contain atLeastOneElementOf List(1L, 2L, 3L, 4L), 10.seconds)
    }

    "keep the same order for data and context when using unsafeDataVia" in {
      val data = List(("1", 1), ("2", 2), ("3", 3), ("4", 4))

      val baseFlow = Flow[(String, Int)]
        .asFlowWithContext[String, Int, Int](collapseContext = Tuple2.apply)(extractContext = _._2)
        .map(_._1)
        .unsafeDataVia(Flow.fromFunction[String, Int] { _.toInt })

      SourceWithContext
        .fromTuples(Source(data))
        .via(baseFlow)
        .runWith(TestSink[(Int, Int)]())
        .request(4)
        .expectNext((1, 1), (2, 2), (3, 3), (4, 4))
        .expectComplete()
    }

    "Apply a viaFlow with optional elements using unsafeOptionalVia" in {
      val data = List((Some("1"), 1), (None, 2), (None, 3), (Some("4"), 4))

      val flow = Flow[(Option[String], Int)]
        .asFlowWithContext[Option[String], Int, Int](collapseContext = Tuple2.apply)(extractContext = _._2)
        .map(_._1)

      for (_ <- 0 until 64) {
        SourceWithContext
          .fromTuples(Source(data)).via(
            FlowWithContext.unsafeOptionalDataVia(
              flow,
              Flow.fromFunction { (string: String) => string.toInt }
            )(Keep.none)
          )
          .runWith(TestSink[(Option[Int], Int)]())
          .request(4)
          .expectNext((Some(1), 1), (None, 2), (None, 3), (Some(4), 4))
          .expectComplete()
      }
    }
  }
}
