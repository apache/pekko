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

package org.apache.pekko.stream.scaladsl

import org.apache.pekko
import pekko.stream.{ AbruptTerminationException, Materializer }
import pekko.stream.testkit.{ StreamSpec, TestPublisher }

import scala.collection.immutable
import scala.concurrent.{ Await, Future }

class CollectionSinkSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) {

  "Sink.collection" when {
    "using Seq as Collection" must {
      "return a Seq[T] from a Source" in {
        val input = 1 to 6
        val future: Future[immutable.Seq[Int]] = Source(input).runWith(Sink.collection)
        val result: immutable.Seq[Int] = Await.result(future, remainingOrDefault)
        result should be(input.toSeq)
      }

      "return an empty Seq[T] from an empty Source" in {
        val input: immutable.Seq[Int] = Nil
        val future: Future[immutable.Seq[Int]] = Source.fromIterator(() => input.iterator).runWith(Sink.collection)
        val result: immutable.Seq[Int] = Await.result(future, remainingOrDefault)
        result should be(input)
      }

      "fail the future on abrupt termination" in {
        val mat = Materializer(system)
        val probe = TestPublisher.probe()
        val future = Source.fromPublisher(probe).runWith(Sink.collection[Unit, Seq[Unit]])(mat)
        mat.shutdown()
        future.failed.futureValue shouldBe an[AbruptTerminationException]
      }
    }
    "using Vector as Collection" must {
      "return a Vector[T] from a Source" in {
        val input = 1 to 6
        val future: Future[immutable.Vector[Int]] = Source(input).runWith(Sink.collection)
        val result: immutable.Vector[Int] = Await.result(future, remainingOrDefault)
        result should be(input.toVector)
      }

      "return an empty Vector[T] from an empty Source" in {
        val input = Nil
        val future: Future[immutable.Vector[Int]] =
          Source.fromIterator(() => input.iterator).runWith(Sink.collection[Int, Vector[Int]])
        val result: immutable.Vector[Int] = Await.result(future, remainingOrDefault)
        result should be(Vector.empty[Int])
      }
    }
  }
}
