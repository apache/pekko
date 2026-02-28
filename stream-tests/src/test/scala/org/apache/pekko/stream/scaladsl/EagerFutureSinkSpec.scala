/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.{ Future, Promise }

import org.apache.pekko
import pekko.stream.{ AbruptStageTerminationException, Materializer }
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils._

class EagerFutureSinkSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 1
    pekko.stream.materializer.max-input-buffer-size = 1
  """) {

  val ex = TE("")

  "Sink.eagerFutureSink" must {

    "work with an already-completed future" in {
      val result = Source(List(1, 2, 3))
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.seq[Int])))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe Seq(1, 2, 3)
    }

    "work when the future completes after elements arrive" in {
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source(List(1, 2, 3))
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()
        .flatten

      sinkPromise.success(Sink.seq[Int])
      result.futureValue shouldBe Seq(1, 2, 3)
    }

    "handle an empty stream with an already-completed future" in {
      val result = Source
        .empty[Int]
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.seq[Int])))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe Seq.empty
    }

    "handle an empty stream with a pending future" in {
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source
        .empty[Int]
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()
        .flatten

      sinkPromise.success(Sink.seq[Int])
      result.futureValue shouldBe Seq.empty
    }

    "propagate failure when the future fails" in {
      val result = Source(List(1, 2, 3))
        .toMat(Sink.eagerFutureSink(Future.failed[Sink[Int, Future[Seq[Int]]]](ex)))(Keep.right)
        .run()
        .flatten

      result.failed.futureValue shouldBe ex
    }

    "propagate upstream failure" in {
      val result = Source
        .failed[Int](ex)
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.seq[Int])))(Keep.right)
        .run()
        .flatten

      result.failed.futureValue shouldBe ex
    }

    "propagate upstream failure when the future is still pending" in {
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source
        .failed[Int](ex)
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()
        .flatten

      sinkPromise.success(Sink.seq[Int])
      result.failed.futureValue shouldBe ex
    }

    "propagate upstream failure when element was buffered and future resolves later" in {
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source(List(1))
        .concat(Source.failed[Int](ex))
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()
        .flatten

      sinkPromise.success(Sink.seq[Int])
      result.failed.futureValue shouldBe ex
    }

    "work with Sink.fold on a non-empty stream" in {
      val result = Source(List(1, 2, 3))
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.fold[Int, Int](0)(_ + _))))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe 6
    }

    "work with Sink.fold on an empty stream" in {
      val result = Source
        .empty[Int]
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.fold[Int, Int](0)(_ + _))))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe 0
    }

    "not throw NeverMaterializedException on empty stream (unlike futureSink)" in {
      val result = Source
        .empty[Int]
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.seq[Int])))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe Seq.empty
    }

    "materialize inner sink immediately when the future is already completed (even with no elements yet)" in {
      val innerMatPromise = Promise[Unit]()
      val sink = Sink.foreach[Int](_ => ()).mapMaterializedValue(_ => innerMatPromise.success(()))
      val sinkFuture = Future.successful(sink)

      Source.maybe[Int]
        .toMat(Sink.eagerFutureSink(sinkFuture))(Keep.right)
        .run()

      innerMatPromise.future.futureValue shouldBe (())
    }

    "cancel upstream when inner sink cancels" in {
      val result = Source(List(1, 2, 3, 4, 5))
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.head[Int])))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe 1
    }

    "propagate failure when the future fails late" in {
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source(List(1, 2, 3))
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()

      sinkPromise.failure(ex)
      result.failed.futureValue shouldBe ex
    }

    "fail the materialized value on abrupt termination before future completion" in {
      val mat = Materializer(system)
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source.maybe[Int]
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()(mat)

      mat.shutdown()

      result.failed.futureValue shouldBe an[AbruptStageTerminationException]
    }
  }
}
