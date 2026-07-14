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

import java.util.Collections
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.switch
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.control.NoStackTrace

import org.apache.pekko

import org.scalatest.time.{ Seconds, Span }

import pekko.NotUsed
import pekko.pattern.FutureTimeoutSupport
import pekko.stream._
import pekko.stream.testkit.{ ScriptedTest, StreamSpec }
import pekko.stream.testkit.scaladsl.TestSink

class FlowFlatMapConcatParallelismSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest with FutureTimeoutSupport {

  // 100K-element tests need extra headroom, especially on JDK 25+ where
  // ForkJoinPool scheduling changes slow down highly-parallel workloads (#2573).
  // Dilation makes the timeout track CI's pekko.test.timefactor.
  override implicit val patience: PatienceConfig =
    PatienceConfig(
      timeout = Span((60 * testKitSettings.TestTimeFactor).toLong, Seconds),
      interval = Span(1, Seconds))

  val toSeq = Flow[Int].grouped(1000).toMat(Sink.head)(Keep.right)

  class BoomException extends RuntimeException("BOOM~~") with NoStackTrace

  val checkValues = List(1, 2, 4, 8, 16, 32, 64, 128)

  "A flatMapConcat" must {

    for (i <- checkValues) {
      s"work with value presented sources with parallelism: $i" in {
        Source(
          List(
            Source.empty[Int],
            Source.single(1),
            Source.empty[Int],
            Source(List(2, 3, 4)),
            Source.future(Future.successful(5)),
            Source.lazyFuture(() => Future.successful(6)),
            Source.future(after(1.millis)(Future.successful(7)))))
          .flatMapConcat(i, identity)
          .runWith(toSeq)
          .futureValue should ===(1 to 7)
      }
    }

    def generateRandomValuePresentedSources(nums: Int): (Int, List[Source[Int, NotUsed]]) = {
      val seq = List.tabulate(nums) { _ =>
        val random = ThreadLocalRandom.current().nextInt(1, 10)
        (random: @switch) match {
          case 1 => Source.single(1)
          case 2 => Source(List(1))
          case 3 => Source.fromJavaStream(() => Collections.singleton(1).stream())
          case 4 => Source.future(Future.successful(1))
          case 5 => Source.future(after(1.millis)(Future.successful(1)))
          case _ => Source.empty[Int]
        }
      }
      val sum = seq.filterNot(_.eq(Source.empty[Int])).size
      (sum, seq)
    }

    def generateSequencedValuePresentedSources(nums: Int): (Int, List[Source[Int, NotUsed]]) = {
      val seq = List.tabulate(nums) { index =>
        val random = ThreadLocalRandom.current().nextInt(1, 6)
        (random: @switch) match {
          case 1 => Source.single(index)
          case 2 => Source(List(index))
          case 3 => Source.fromJavaStream(() => Collections.singleton(index).stream())
          case 4 => Source.future(Future.successful(index))
          case 5 => Source.future(after(1.millis)(Future.successful(index)))
          case _ => throw new IllegalStateException("unexpected")
        }
      }
      val sum = (0 until nums).sum
      (sum, seq)
    }

    for (i <- checkValues) {
      s"work with generated value presented sources with parallelism: $i " in {
        val (sum, sources @ _) = generateRandomValuePresentedSources(100000)
        Source(sources)
          .flatMapConcat(i, identity(_)) // scala 2.12 can't infer the type of identity
          .runWith(Sink.seq)
          .map(_.sum)(scala.concurrent.ExecutionContext.parasitic)
          .futureValue shouldBe sum
      }
    }

    for (i <- checkValues) {
      s"work with generated value sequenced sources with parallelism: $i " in {
        val (sum, sources @ _) = generateSequencedValuePresentedSources(100000)
        Source(sources)
          .flatMapConcat(i, identity(_)) // scala 2.12 can't infer the type of identity
          // check the order
          .statefulMap(() => -1)((pre, current) => {
              if (pre + 1 != current) {
                throw new IllegalStateException(s"expected $pre + 1 == $current")
              }
              (current, current)
            }, _ => None)
          .runWith(Sink.seq)
          .map(_.sum)(scala.concurrent.ExecutionContext.parasitic)
          .futureValue shouldBe sum
      }
    }

    "work with value presented failed sources" in {
      val ex = new BoomException
      Source(
        List(
          Source.empty[Int],
          Source.single(1),
          Source.empty[Int],
          Source(List(2, 3, 4)),
          Source.future(Future.failed(ex)),
          Source.lazyFuture(() => Future.successful(5))))
        .flatMapConcat(ThreadLocalRandom.current().nextInt(1, 129), identity)
        .onErrorComplete[BoomException]()
        .runWith(toSeq)
        .futureValue should ===(1 to 4)
    }

    "treat Success(null) future inner sources as completion-without-element" in {
      // Mirrors GraphStages.FutureSource semantics: Success(null) completes the inner
      // source without emitting. The optimized fast path must match this.
      val toIntegerSeq = Flow[Integer].grouped(1000).toMat(Sink.head)(Keep.right)
      Source(
        List[Source[Integer, NotUsed]](
          Source.future(Future.successful[Integer](null)),
          Source.single[Integer](1),
          Source.future(after(1.millis)(Future.successful[Integer](null))),
          Source.lazyFuture(() => Future.successful[Integer](null)),
          Source.single[Integer](2)))
        .flatMapConcat(ThreadLocalRandom.current().nextInt(1, 129), identity)
        .runWith(toIntegerSeq)
        .futureValue should ===(Seq[Integer](1, 2))
    }

    "propagate inner-source failure queued behind a Success(null) pending future" in {
      // Regression: tryPullNextSourceInQueue used to call src.tryPull() for
      // InflightSource heads, which is a no-op for already-settled sources
      // (e.g. InflightCompletedFutureSource(Failure(_))). Once the pending
      // Success(null) head was removed, the failure sat in the queue forever
      // because nothing surfaced it. Drive the head via pushOut instead.
      val ex = new BoomException
      val probe = Source(
        List[Source[Integer, NotUsed]](
          Source.future(after(1.millis)(Future.successful[Integer](null))),
          Source.failed[Integer](ex)))
        .flatMapConcat(parallelism = 4, identity)
        .runWith(TestSink())
      probe.request(1)
      probe.expectError() should ===(ex)
    }

    "emit completed future queued behind a Success(null) pending future" in {
      // Same root cause as above: an InflightCompletedFutureSource(Success(_))
      // queued behind a Success(null) pending future was stranded because
      // tryPull() is a no-op on already-settled sources.
      val toIntegerSeq = Flow[Integer].grouped(1000).toMat(Sink.head)(Keep.right)
      Source(
        List[Source[Integer, NotUsed]](
          Source.future(after(1.millis)(Future.successful[Integer](null))),
          Source.future(Future.successful[Integer](42))))
        .flatMapConcat(parallelism = 4, identity)
        .runWith(toIntegerSeq)
        .futureValue should ===(Seq[Integer](42))
    }

    "work with value presented sources when demands slow" in {
      val prob = Source(
        List(Source.empty[Int], Source.single(1), Source(List(2, 3, 4)), Source.lazyFuture(() => Future.successful(5))))
        .flatMapConcat(ThreadLocalRandom.current().nextInt(1, 129), identity)
        .runWith(TestSink())

      prob.request(1)
      prob.expectNext(1)
      prob.expectNoMessage(1.seconds)
      prob.request(2)
      prob.expectNext(2, 3)
      prob.expectNoMessage(1.seconds)
      prob.request(2)
      prob.expectNext(4, 5)
      prob.expectComplete()
    }

    val parallelism = ThreadLocalRandom.current().nextInt(4, 65)
    s"can do pre materialization when parallelism > 1, parallelism is $parallelism" in {
      val materializationCounter = new AtomicInteger(0)
      val prob = Source(1 to (parallelism * 3))
        .flatMapConcat(
          parallelism,
          value => {
            Source
              .lazySingle(() => {
                materializationCounter.incrementAndGet()
                value
              })
              .buffer(1, overflowStrategy = OverflowStrategy.backpressure)
          })
        .runWith(TestSink())

      expectNoMessage(1.seconds)
      materializationCounter.get() shouldBe 0

      prob.request(1)
      prob.expectNext(1.seconds, 1)
      expectNoMessage(1.seconds)
      materializationCounter.get() shouldBe (parallelism + 1)
      materializationCounter.set(0)

      prob.request(2)
      prob.expectNextN(List(2, 3))
      expectNoMessage(1.seconds)
      materializationCounter.get() shouldBe 2
      materializationCounter.set(0)

      prob.request(parallelism - 3)
      prob.expectNextN(4 to parallelism)
      expectNoMessage(1.seconds)
      materializationCounter.get() shouldBe (parallelism - 3)
      materializationCounter.set(0)

      prob.request(parallelism)
      prob.expectNextN(parallelism + 1 to parallelism * 2)
      expectNoMessage(1.seconds)
      materializationCounter.get() shouldBe parallelism
      materializationCounter.set(0)

      prob.request(parallelism)
      prob.expectNextN(parallelism * 2 + 1 to parallelism * 3)
      expectNoMessage(1.seconds)
      materializationCounter.get() shouldBe 0
      prob.expectComplete()
    }
  }
}
