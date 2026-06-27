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

case class Message(data: String, offset: Long)

class SourceWithContextSpec extends StreamSpec {

  "A SourceWithContext" must {

    "get created from Source.asSourceWithContext" in {
      val msg = Message("a", 1L)
      Source(Vector(msg))
        .asSourceWithContext(_.offset)
        .toMat(TestSink[(Message, Long)]())(Keep.right)
        .run()
        .request(1)
        .expectNext((msg, 1L))
        .expectComplete()
    }

    "get created from a source of tuple2" in {
      val msg = Message("a", 1L)
      SourceWithContext
        .fromTuples(Source(Vector((msg, msg.offset))))
        .asSource
        .runWith(TestSink[(Message, Long)]())
        .request(1)
        .expectNext((msg, 1L))
        .expectComplete()
    }

    "be able to get turned back into a normal Source" in {
      val msg = Message("a", 1L)
      Source(Vector(msg))
        .asSourceWithContext(_.offset)
        .map(_.data)
        .asSource
        .map { case (e, _) => e }
        .runWith(TestSink[String]())
        .request(1)
        .expectNext("a")
        .expectComplete()
    }

    "pass through contexts using map and filter" in {
      Source(Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L)))
        .asSourceWithContext(_.offset)
        .map(_.data.toLowerCase)
        .filter(_ != "b")
        .filterNot(_ == "d")
        .toMat(TestSink[(String, Long)]())(Keep.right)
        .run()
        .request(2)
        .expectNext(("a", 1L))
        .expectNext(("c", 4L))
        .expectComplete()
    }

    "pass through contexts using additional filtering operators" in {
      SourceWithContext
        .fromTuples(Source(Vector((0, "zero"), (1, "one"), (2, "two"), (3, "three"))))
        .mapOption {
          case 0     => None
          case value => Some(value * 10)
        }
        .runWith(TestSink[(Int, String)]())
        .request(3)
        .expectNext((10, "one"), (20, "two"), (30, "three"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source(Vector((1, "one"), (2, "two"), (3, "three"))))
        .collectFirst { case value if value > 1 => value * 10 }
        .runWith(TestSink[(Int, String)]())
        .request(1)
        .expectNext((20, "two"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source(Vector((1, "one"), (2, "two"), (3, "three"))))
        .collectWhile { case value if value < 3 => value * 10 }
        .runWith(TestSink[(Int, String)]())
        .request(3)
        .expectNext((10, "one"), (20, "two"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source(Vector((1: Any, "one"), ("two": Any, "two"), (3: Any, "three"))))
        .collectType[Int]
        .runWith(TestSink[(Int, String)]())
        .request(2)
        .expectNext((1, "one"), (3, "three"))
        .expectComplete()

      val forComprehensionResult =
        for {
          value <- SourceWithContext.fromTuples(Source(Vector((1, "one"), (2, "two"), (3, "three"))))
          if value % 2 == 1
        } yield value * 10

      forComprehensionResult
        .runWith(TestSink[(Int, String)]())
        .request(2)
        .expectNext((10, "one"), (30, "three"))
        .expectComplete()
    }

    "pass through contexts using truncating operators" in {
      SourceWithContext
        .fromTuples(Source(Vector((0, "zero"), (1, "one"), (1, "one-duplicate"), (2, "two"), (3, "three"),
          (4, "four"))))
        .drop(1)
        .dropRepeated()
        .dropWhile(_ < 2)
        .takeUntil(_ == 3)
        .takeWithin(1.day)
        .take(2)
        .limit(2)
        .limitWeighted(2)(_ => 1)
        .runWith(TestSink[(Int, String)]())
        .request(2)
        .expectNext((2, "two"), (3, "three"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source(Vector((1, "one"), (2, "two"), (3, "three"))))
        .takeWhile(_ < 2, inclusive = true)
        .runWith(TestSink[(Int, String)]())
        .request(2)
        .expectNext((1, "one"), (2, "two"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source(Vector((1, "one"), (3, "three"), (4, "four"))))
        .dropRepeated((left, right) => left % 2 == right % 2)
        .takeWhile(_ < 3)
        .runWith(TestSink[(Int, String)]())
        .request(2)
        .expectNext((1, "one"))
        .expectComplete()

      SourceWithContext
        .fromTuples(Source.single((1, "one")).initialDelay(50.millis))
        .dropWithin(10.millis)
        .takeWithin(1.day)
        .runWith(TestSink[(Int, String)]())
        .request(1)
        .expectNext((1, "one"))
        .expectComplete()
    }

    "pass through all data when using alsoTo" in {
      // alsoTo feeds an asynchronous side Sink, which may still be draining when the
      // main stream completes. Poll until it has observed every element instead of
      // asserting once (the single assertion raced under JDK 25 scheduling).
      val received = new ConcurrentLinkedQueue[Message]()
      val messages = Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L))
      Source(messages)
        .asSourceWithContext(_.offset)
        .alsoTo(Sink.foreach(message => received.add(message)))
        .toMat(TestSink[(Message, Long)]())(Keep.right)
        .run()
        .request(4)
        .expectNext((Message("A", 1L), 1L))
        .expectNext((Message("B", 2L), 2L))
        .expectNext((Message("D", 3L), 3L))
        .expectNext((Message("C", 4L), 4L))
        .expectComplete()
      awaitAssert(received.asScala.toVector shouldBe messages, 10.seconds)
    }

    "pass through all data when using alsoToContext" in {
      val received = new ConcurrentLinkedQueue[Long]()
      val messages = Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L))
      Source(messages)
        .asSourceWithContext(_.offset)
        .alsoToContext(Sink.foreach(offset => received.add(offset)))
        .toMat(TestSink[(Message, Long)]())(Keep.right)
        .run()
        .request(4)
        .expectNext((Message("A", 1L), 1L))
        .expectNext((Message("B", 2L), 2L))
        .expectNext((Message("D", 3L), 3L))
        .expectNext((Message("C", 4L), 4L))
        .expectComplete()
      awaitAssert(received.asScala.toVector shouldBe messages.map(_.offset), 10.seconds)
    }

    "pass through all data when using wireTap" in {
      val received = new ConcurrentLinkedQueue[Message]()
      val messages = Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L))
      Source(messages)
        .asSourceWithContext(_.offset)
        .wireTap(Sink.foreach(message => received.add(message)))
        .toMat(TestSink[(Message, Long)]())(Keep.right)
        .run()
        .request(4)
        .expectNext((Message("A", 1L), 1L))
        .expectNext((Message("B", 2L), 2L))
        .expectNext((Message("D", 3L), 3L))
        .expectNext((Message("C", 4L), 4L))
        .expectComplete()
      awaitAssert(received.asScala.toVector should contain atLeastOneElementOf messages, 10.seconds)
    }

    "pass through all data when using wireTapContext" in {
      val received = new ConcurrentLinkedQueue[Long]()
      val messages = Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L))
      Source(messages)
        .asSourceWithContext(_.offset)
        .wireTapContext(Sink.foreach(offset => received.add(offset)))
        .toMat(TestSink[(Message, Long)]())(Keep.right)
        .run()
        .request(4)
        .expectNext((Message("A", 1L), 1L))
        .expectNext((Message("B", 2L), 2L))
        .expectNext((Message("D", 3L), 3L))
        .expectNext((Message("C", 4L), 4L))
        .expectComplete()
      awaitAssert(received.asScala.toVector should contain atLeastOneElementOf (messages.map(_.offset)), 10.seconds)
    }

    "pass through contexts via a FlowWithContext" in {

      def flowWithContext[T] = FlowWithContext[T, Long]

      Source(Vector(Message("a", 1L)))
        .asSourceWithContext(_.offset)
        .map(_.data)
        .via(flowWithContext.map(s => s + "b"))
        .runWith(TestSink[(String, Long)]())
        .request(1)
        .expectNext(("ab", 1L))
        .expectComplete()
    }

    "pass through contexts via mapConcat" in {
      Source(Vector(Message("a", 1L)))
        .asSourceWithContext(_.offset)
        .map(_.data)
        .mapConcat { str =>
          List(1, 2, 3).map(i => s"$str-$i")
        }
        .runWith(TestSink[(String, Long)]())
        .request(3)
        .expectNext(("a-1", 1L), ("a-2", 1L), ("a-3", 1L))
        .expectComplete()
    }

    "pass through a sequence of contexts per element via grouped" in {
      Source(Vector(Message("a", 1L)))
        .asSourceWithContext(_.offset)
        .map(_.data)
        .mapConcat { str =>
          List(1, 2, 3, 4).map(i => s"$str-$i")
        }
        .grouped(2)
        .toMat(TestSink[(Seq[String], Seq[Long])]())(Keep.right)
        .run()
        .request(2)
        .expectNext((Seq("a-1", "a-2"), Seq(1L, 1L)), (Seq("a-3", "a-4"), Seq(1L, 1L)))
        .expectComplete()
    }

    "be able to change materialized value via mapMaterializedValue" in {
      val materializedValue = "MatedValue"
      Source
        .empty[Message]
        .asSourceWithContext(_.offset)
        .mapMaterializedValue(_ => materializedValue)
        .to(Sink.ignore)
        .run() shouldBe materializedValue
    }

    "be able to map error via mapError" in {
      val ex = new RuntimeException("ex") with NoStackTrace
      val boom = new Exception("BOOM!") with NoStackTrace

      Source(1L to 4L)
        .map { offset =>
          Message("a", offset)
        }
        .map {
          case m @ Message(_, offset) => if (offset == 3) throw ex else m
        }
        .asSourceWithContext(_.offset)
        .mapError { case _: Throwable => boom }
        .runWith(TestSink[(Message, Long)]())
        .request(3)
        .expectNext((Message("a", 1L), 1L))
        .expectNext((Message("a", 2L), 2L))
        .expectError(boom)
    }

    "keep the same order for data and context when using unsafeDataVia" in {
      val data = List(("1", 1), ("2", 2), ("3", 3), ("4", 4))

      SourceWithContext
        .fromTuples(Source(data))
        .unsafeDataVia(Flow.fromFunction[String, Int] { _.toInt })
        .runWith(TestSink[(Int, Int)]())
        .request(4)
        .expectNext((1, 1), (2, 2), (3, 3), (4, 4))
        .expectComplete()
    }

    "Apply a viaFlow with optional elements using unsafeOptionalVia" in {
      val data = List((Some("1"), 1), (None, 2), (None, 3), (Some("4"), 4))

      val source = SourceWithContext.fromTuples(Source(data))

      for (_ <- 0 until 64) {
        SourceWithContext.unsafeOptionalDataVia(
          source,
          Flow.fromFunction { (string: String) => string.toInt }
        )(Keep.none)
          .runWith(TestSink[(Option[Int], Int)]())
          .request(4)
          .expectNext((Some(1), 1), (None, 2), (None, 3), (Some(4), 4))
          .expectComplete()
      }
    }
  }
}
