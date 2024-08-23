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

import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSink

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

case class Message(data: String, offset: Long)

class SourceWithContextSpec extends StreamSpec {

  "A SourceWithContext" must {

    "get created from Source.asSourceWithContext" in {
      val msg = Message("a", 1L)
      Source(Vector(msg))
        .asSourceWithContext(_.offset)
        .toMat(TestSink.probe[(Message, Long)])(Keep.right)
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
        .runWith(TestSink.probe[(Message, Long)])
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
        .runWith(TestSink.probe[String])
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
        .toMat(TestSink.probe[(String, Long)])(Keep.right)
        .run()
        .request(2)
        .expectNext(("a", 1L))
        .expectNext(("c", 4L))
        .expectComplete()
    }

    "pass through all data when using alsoTo" in {
      val listBuffer = new ListBuffer[Message]()
      val messages = Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L))
      Source(messages)
        .asSourceWithContext(_.offset)
        .alsoTo(Sink.foreach(message => listBuffer.+=(message)))
        .toMat(TestSink.probe[(Message, Long)])(Keep.right)
        .run()
        .request(4)
        .expectNext((Message("A", 1L), 1L))
        .expectNext((Message("B", 2L), 2L))
        .expectNext((Message("D", 3L), 3L))
        .expectNext((Message("C", 4L), 4L))
        .expectComplete()
        .within(10.seconds) {
          listBuffer.toVector shouldBe messages
        }
    }

    "pass through all data when using alsoToContext" in {
      val listBuffer = new ListBuffer[Long]()
      val messages = Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L))
      Source(messages)
        .asSourceWithContext(_.offset)
        .alsoToContext(Sink.foreach(offset => listBuffer.+=(offset)))
        .toMat(TestSink.probe[(Message, Long)])(Keep.right)
        .run()
        .request(4)
        .expectNext((Message("A", 1L), 1L))
        .expectNext((Message("B", 2L), 2L))
        .expectNext((Message("D", 3L), 3L))
        .expectNext((Message("C", 4L), 4L))
        .expectComplete()
        .within(10.seconds) {
          listBuffer.toVector shouldBe messages.map(_.offset)
        }
    }

    "pass through all data when using wireTap" in {
      val listBuffer = new ListBuffer[Message]()
      val messages = Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L))
      Source(messages)
        .asSourceWithContext(_.offset)
        .wireTap(Sink.foreach(message => listBuffer.+=(message)))
        .toMat(TestSink.probe[(Message, Long)])(Keep.right)
        .run()
        .request(4)
        .expectNext((Message("A", 1L), 1L))
        .expectNext((Message("B", 2L), 2L))
        .expectNext((Message("D", 3L), 3L))
        .expectNext((Message("C", 4L), 4L))
        .expectComplete()
        .within(10.seconds) {
          listBuffer.toVector should contain atLeastOneElementOf messages
        }
    }

    "pass through all data when using wireTapContext" in {
      val listBuffer = new ListBuffer[Long]()
      val messages = Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L))
      Source(messages)
        .asSourceWithContext(_.offset)
        .wireTapContext(Sink.foreach(offset => listBuffer.+=(offset)))
        .toMat(TestSink.probe[(Message, Long)])(Keep.right)
        .run()
        .request(4)
        .expectNext((Message("A", 1L), 1L))
        .expectNext((Message("B", 2L), 2L))
        .expectNext((Message("D", 3L), 3L))
        .expectNext((Message("C", 4L), 4L))
        .expectComplete()
        .within(10.seconds) {
          listBuffer.toVector should contain atLeastOneElementOf (messages.map(_.offset))
        }
    }

    "pass through contexts via a FlowWithContext" in {

      def flowWithContext[T] = FlowWithContext[T, Long]

      Source(Vector(Message("a", 1L)))
        .asSourceWithContext(_.offset)
        .map(_.data)
        .via(flowWithContext.map(s => s + "b"))
        .runWith(TestSink.probe[(String, Long)])
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
        .runWith(TestSink.probe[(String, Long)])
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
        .toMat(TestSink.probe[(Seq[String], Seq[Long])])(Keep.right)
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
        .runWith(TestSink.probe[(Message, Long)])
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
        .runWith(TestSink.probe[(Int, Int)])
        .request(4)
        .expectNext((1, 1), (2, 2), (3, 3), (4, 4))
        .expectComplete()
    }

    "Apply a viaFlow with optional elements using unsafeOptionalVia" in {
      val data = List((Some("1"), 1), (None, 2), (None, 3), (Some("4"), 4))

      val source = SourceWithContext.fromTuples(Source(data))

      SourceWithContext.unsafeOptionalDataVia(
        source,
        Flow.fromFunction { (string: String) => string.toInt }
      )(Keep.none)
        .runWith(TestSink.probe[(Option[Int], Int)])
        .request(4)
        .expectNext((Some(1), 1), (None, 2), (None, 3), (Some(4), 4))
        .expectComplete()
    }
  }
}
