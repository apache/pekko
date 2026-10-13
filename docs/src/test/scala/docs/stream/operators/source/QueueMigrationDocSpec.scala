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

package docs.stream.operators.source

import scala.annotation.nowarn
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.actor.ActorRef
import pekko.pattern.ask
import pekko.stream.BoundedSourceQueue
import pekko.stream.BufferOverflowException
import pekko.stream.CompletionStrategy
import pekko.stream.OverflowStrategy
import pekko.stream.QueueOfferResult
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.MergeHub
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.scaladsl.SourceQueueWithComplete
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.PekkoSpec
import pekko.util.Timeout

class QueueMigrationDocSpec extends PekkoSpec {
  import system.dispatcher

  "Source.queue migration" should {

    "replace dropTail with Source.queue(bufferSize)" in {
      @nowarn("msg=deprecated")
      def before(): Future[QueueOfferResult] = {
        // #drop-before
        val queue: SourceQueueWithComplete[Int] =
          Source.queue[Int](100, OverflowStrategy.dropTail).to(Sink.foreach(println)).run()

        val result: Future[QueueOfferResult] = queue.offer(1)
        // #drop-before
        result
      }
      before().futureValue shouldBe QueueOfferResult.Enqueued

      // #drop-after
      val queue: BoundedSourceQueue[Int] =
        Source.queue[Int](100).to(Sink.foreach(println)).run()

      // offer returns the result synchronously, there is no Future to wait for
      queue.offer(1) match {
        case QueueOfferResult.Enqueued    => // element accepted
        case QueueOfferResult.Dropped     => // buffer full, this element was dropped
        case QueueOfferResult.Failure(ex) => // the stream failed
        case QueueOfferResult.QueueClosed => // the stream was completed
      }
      // #drop-after
    }

    "replace fail with Source.queue(bufferSize) and BoundedSourceQueue.fail" in {
      // #fail-after
      val bufferSize = 2

      // fail the stream like OverflowStrategy.fail did when the buffer is full
      def offerOrFail(queue: BoundedSourceQueue[Int], elem: Int): QueueOfferResult = {
        val result = queue.offer(elem)
        if (result == QueueOfferResult.Dropped)
          queue.fail(BufferOverflowException(s"Buffer overflow (max capacity was: $bufferSize)!"))
        result
      }
      // #fail-after

      // downstream never requests, so the buffer fills up
      val (queue, probe) = Source.queue[Int](bufferSize).toMat(TestSink[Int]())(Keep.both).run()
      offerOrFail(queue, 1) shouldBe QueueOfferResult.Enqueued
      offerOrFail(queue, 2) shouldBe QueueOfferResult.Enqueued
      offerOrFail(queue, 3) shouldBe QueueOfferResult.Dropped
      probe.expectSubscriptionAndError() shouldBe a[BufferOverflowException]
    }

    "replace backpressure with Source.actorRefWithBackpressure for a single producer" in {
      @nowarn("msg=deprecated")
      def before(): Future[Done] = {
        // #backpressure-before
        val queue: SourceQueueWithComplete[Int] =
          Source.queue[Int](100, OverflowStrategy.backpressure).to(Sink.foreach(println)).run()

        // the next offer is only made once the previous one has completed
        val allOffered: Future[QueueOfferResult] = (1 to 10).foldLeft(
          Future.successful[QueueOfferResult](QueueOfferResult.Enqueued)) { (previous, elem) =>
          previous.flatMap(_ => queue.offer(elem))
        }
        allOffered.map(_ => queue.complete())
        // #backpressure-before
        queue.watchCompletion()
      }
      before().futureValue shouldBe Done

      // #backpressure-single-producer
      case object Ack
      case object Complete

      val (ref: ActorRef, done: Future[Seq[Int]]) =
        Source
          .actorRefWithBackpressure[Int](
            ackMessage = Ack,
            completionMatcher = { case Complete => CompletionStrategy.draining },
            failureMatcher = PartialFunction.empty)
          .toMat(Sink.seq)(Keep.both)
          .run()

      // Replaces queue.offer(elem): the Future completes with Ack once the element was emitted
      // downstream. Unlike the deprecated queue it fails with an AskTimeoutException instead of
      // hanging forever if downstream stalls.
      implicit val timeout: Timeout = 3.seconds
      def offer(elem: Int): Future[Any] = ref ? elem

      // Like before, the next element must only be offered once the previous one was acknowledged,
      // otherwise the stream fails.
      val allOffered: Future[Any] = (1 to 10).foldLeft(Future.successful[Any](Ack)) { (previous, elem) =>
        previous.flatMap(_ => offer(elem))
      }
      allOffered.foreach(_ => ref ! Complete)
      // #backpressure-single-producer

      done.futureValue shouldBe (1 to 10)
    }

    "replace backpressure with MergeHub.source for multiple producers" in {
      // #backpressure-multiple-producers
      val (sink: Sink[Int, NotUsed], done: Future[Seq[Int]]) =
        MergeHub.source[Int](perProducerBufferSize = 16).take(20).toMat(Sink.seq)(Keep.both).run()

      // Each producer is a stream of its own and is back-pressured independently,
      // there is no offer Future to track per element.
      Source(1 to 10).runWith(sink)
      Source(11 to 20).runWith(sink)
      // #backpressure-multiple-producers

      done.futureValue should contain theSameElementsAs (1 to 20)
    }
  }
}
