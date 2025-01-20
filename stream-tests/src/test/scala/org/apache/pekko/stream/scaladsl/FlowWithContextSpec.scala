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
        .runWith(TestSink.probe[(Message, Long)])
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
        .toMat(TestSink.probe[(Message, Long)])(Keep.both)
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
        .runWith(TestSink.probe[(Message, Long)])
        .request(3)
        .expectNext((Message("a", 1L), 1L))
        .expectNext((Message("a", 2L), 2L))
        .expectError(boom)
    }

    "pass through all data when using alsoTo" in {
      val listBuffer = new ListBuffer[String]()
      Source(Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L)))
        .asSourceWithContext(_.offset)
        .via(
          FlowWithContext.fromTuples(Flow.fromFunction[(Message, Long), (String, Long)] { case (data, offset) =>
            (data.data.toLowerCase, offset)
          })
            .alsoTo(Sink.foreach(string => listBuffer.+=(string)))
        )
        .toMat(TestSink.probe[(String, Long)])(Keep.right)
        .run()
        .request(4)
        .expectNext(("a", 1L))
        .expectNext(("b", 2L))
        .expectNext(("d", 3L))
        .expectNext(("c", 4L))
        .expectComplete()
        .within(10.seconds) {
          listBuffer should contain theSameElementsInOrderAs List("a", "b", "d", "c")
        }
    }

    "pass through all data when using alsoToContext" in {
      val listBuffer = new ListBuffer[Long]()
      Source(Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L)))
        .asSourceWithContext(_.offset)
        .via(
          FlowWithContext.fromTuples(Flow.fromFunction[(Message, Long), (String, Long)] { case (data, offset) =>
            (data.data.toLowerCase, offset)
          })
            .alsoToContext(Sink.foreach(offset => listBuffer.+=(offset)))
        )
        .toMat(TestSink.probe[(String, Long)])(Keep.right)
        .run()
        .request(4)
        .expectNext(("a", 1L))
        .expectNext(("b", 2L))
        .expectNext(("d", 3L))
        .expectNext(("c", 4L))
        .expectComplete()
        .within(10.seconds) {
          listBuffer should contain theSameElementsInOrderAs List(1L, 2L, 3L, 4L)
        }
    }

    "pass through all data when using wireTap" in {
      val listBuffer = new ListBuffer[String]()
      Source(Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L)))
        .asSourceWithContext(_.offset)
        .via(
          FlowWithContext.fromTuples(Flow.fromFunction[(Message, Long), (String, Long)] { case (data, offset) =>
            (data.data.toLowerCase, offset)
          }).wireTap(Sink.foreach(string => listBuffer.+=(string)))
        )
        .toMat(TestSink.probe[(String, Long)])(Keep.right)
        .run()
        .request(4)
        .expectNext(("a", 1L))
        .expectNext(("b", 2L))
        .expectNext(("d", 3L))
        .expectNext(("c", 4L))
        .expectComplete()
        .within(10.seconds) {
          listBuffer should contain atLeastOneElementOf List("a", "b", "d", "c")
        }
    }

    "pass through all data when using wireTapContext" in {
      val listBuffer = new ListBuffer[Long]()
      Source(Vector(Message("A", 1L), Message("B", 2L), Message("D", 3L), Message("C", 4L)))
        .asSourceWithContext(_.offset)
        .via(
          FlowWithContext.fromTuples(Flow.fromFunction[(Message, Long), (String, Long)] { case (data, offset) =>
            (data.data.toLowerCase, offset)
          }).wireTapContext(Sink.foreach(offset => listBuffer.+=(offset)))
        )
        .toMat(TestSink.probe[(String, Long)])(Keep.right)
        .run()
        .request(4)
        .expectNext(("a", 1L))
        .expectNext(("b", 2L))
        .expectNext(("d", 3L))
        .expectNext(("c", 4L))
        .expectComplete()
        .within(10.seconds) {
          listBuffer should contain atLeastOneElementOf List(1L, 2L, 3L, 4L)
        }
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
        .runWith(TestSink.probe[(Int, Int)])
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
          .runWith(TestSink.probe[(Option[Int], Int)])
          .request(4)
          .expectNext((Some(1), 1), (None, 2), (None, 3), (Some(4), 4))
          .expectComplete()
      }
    }
  }
}
