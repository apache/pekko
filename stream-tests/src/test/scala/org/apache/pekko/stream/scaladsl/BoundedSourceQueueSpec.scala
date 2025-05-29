/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import java.util.concurrent.{ CountDownLatch, ThreadLocalRandom }
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.QueueOfferResult
import pekko.stream.testkit.{ StreamSpec, TestSubscriber }
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.WithLogCapturing

class BoundedSourceQueueSpec extends StreamSpec("""pekko.loglevel = debug
    |pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    |""".stripMargin) with WithLogCapturing {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(5.seconds)

  val ex = new RuntimeException("oops")

  "BoundedSourceQueue" should {
    "not drop elements if buffer is not full" in {
      val sub = TestSubscriber.probe[Int]()
      val queue =
        Source.queue[Int](100).toMat(Sink.fromSubscriber(sub))(Keep.left).run()

      val elements = 1 to 100

      elements.foreach { i =>
        queue.offer(i) should be(QueueOfferResult.Enqueued)
      }

      queue.complete()
      queue.isCompleted shouldBe true

      val subIt = Iterator.continually(sub.requestNext())
      subIt.zip(elements.iterator).foreach {
        case (subEle, origEle) => subEle should be(origEle)
      }
      sub.expectComplete()
    }

    "drop elements if buffer is full" in {
      val sub = TestSubscriber.probe[Int]()
      val queue =
        Source.queue[Int](10).toMat(Sink.fromSubscriber(sub))(Keep.left).run()

      val elements = 1 to 100

      val histo =
        elements
          .map { i =>
            queue.offer(i)
          }
          .groupBy(identity)
          .map { case (k, v) => (k, v.size) }

      // it should be 100 elements - 10 buffer slots = 90, but there might be other implicit buffers involved
      histo(QueueOfferResult.Dropped) should be > 80
    }

    "buffer size cannot be less than 1" in {
      assertThrows[IllegalArgumentException](Source.queue[Int](0))
    }

    "raise exception if the queue is completed twice" in {
      val sub = TestSubscriber.probe[Int]()
      val queue =
        Source.queue[Int](1).toMat(Sink.fromSubscriber(sub))(Keep.left).run()
      queue.complete()
      queue.isCompleted shouldBe true
      assertThrows[IllegalStateException](queue.complete())
    }

    "raise exception if the queue is failed twice" in {
      val sub = TestSubscriber.probe[Int]()
      val queue =
        Source.queue[Int](1).toMat(Sink.fromSubscriber(sub))(Keep.left).run()
      queue.fail(ex)
      queue.isCompleted shouldBe true
      assertThrows[IllegalStateException](queue.fail(ex))
    }

    "return a QueueClosed result if completed" in {
      val sub = TestSubscriber.probe[Int]()
      val queue =
        Source.queue[Int](10).toMat(Sink.fromSubscriber(sub))(Keep.left).run()

      queue.complete()
      queue.isCompleted shouldBe true
      queue.offer(1) should be(QueueOfferResult.QueueClosed)
      sub.expectSubscriptionAndComplete()
    }

    "return a Failure result if queue failed" in {
      val sub = TestSubscriber.probe[Int]()
      val queue =
        Source.queue[Int](10).toMat(Sink.fromSubscriber(sub))(Keep.left).run()

      queue.fail(ex)
      queue.isCompleted shouldBe true
      queue.offer(1) should be(QueueOfferResult.Failure(ex))
      sub.request(1)
      sub.expectError(ex)
    }

    "return a Failure result if stream failed" in {
      val sub = TestSubscriber.probe[Int]()
      val queue =
        Source.queue[Int](10).map(_ => throw ex).toMat(Sink.fromSubscriber(sub))(Keep.left).run()

      queue.offer(1) should be(QueueOfferResult.Enqueued)
      sub.expectSubscriptionAndError() should be(ex)
      // internal state will be eventually updated when stream cancellation reaches BoundedSourceQueueStage
      awaitAssert(queue.offer(1) should be(QueueOfferResult.Failure(ex)))
    }

    "only flag elements as enqueued that will also be passed to downstream in the absence of cancellation or completion " in {
      val counter = new AtomicLong()
      val (queue, result) =
        Source.queue[Int](100000).toMat(Sink.fold(0L)(_ + _))(Keep.both).run()

      val numThreads = Runtime.getRuntime.availableProcessors() * 4
      val stopProb = 10000 // specifies run time of test indirectly
      val expected = 1d / (1d - math.pow(1d - 1d / stopProb, numThreads))
      log.debug(s"Expected elements per thread: $expected") // variance might be quite high depending on number of threads
      val startBarrier = new CountDownLatch(numThreads)
      val stopBarrier = new CountDownLatch(numThreads)

      class QueueingThread extends Thread {
        override def run(): Unit = {
          var numElemsEnqueued = 0
          var numElemsDropped = 0
          def runLoop(): Unit = {
            val r = ThreadLocalRandom.current()

            var done = false
            while (!done) {
              val i = r.nextInt(0, Int.MaxValue)
              queue.offer(i) match {
                case QueueOfferResult.Enqueued =>
                  counter.addAndGet(i)
                  numElemsEnqueued += 1
                case QueueOfferResult.Dropped =>
                  numElemsDropped += 1
                case unexpected => throw new IllegalStateException(s"Saw $unexpected should not happen in this test")
              }

              if ((i % stopProb) == 0) { // probabilistic exit condition
                done = true
              } else if (i % 100 == 0) Thread.sleep(1) // probabilistic producer throttling delay
            }
          }

          startBarrier.countDown()
          startBarrier.await() // wait for all threads being in this state before starting race
          runLoop()
          stopBarrier.countDown()
          log.debug(
            f"Thread $getName%-20s enqueued: $numElemsEnqueued%7d dropped: $numElemsDropped%7d before completion")
        }
      }

      (1 to numThreads).foreach { i =>
        val t = new QueueingThread
        t.setName(s"QueuingThread-$i")
        t.start()
      }
      stopBarrier.await()
      // if we'd complete from one of the threads and use the QueueCompletedResult as coordination there is a race
      // where enqueueing an element concurrently with Done reaching the stage can lead to Enqueued being returned
      // but the element dropped (no guarantee of entering stream as documented in BoundedSourceQueue.offer
      queue.complete()
      queue.isCompleted shouldBe true

      result.futureValue should be(counter.get())
    }

    // copied from pekko-remote SendQueueSpec
    "deliver bursts of messages" in {
      // this test verifies that the wakeup signal is triggered correctly
      val burstSize = 100
      val (sendQueue, downstream) =
        Source.fromGraph(Source.queue[Int](128)).grouped(burstSize).async.toMat(TestSink.probe)(Keep.both).run()

      downstream.request(10)

      for (round <- 1 to 100000) {
        for (n <- 1 to burstSize) {
          if (sendQueue.offer(round * 1000 + n) != QueueOfferResult.Enqueued)
            fail(s"offer failed at round $round message $n")
        }
        downstream.expectNext((1 to burstSize).map(_ + round * 1000).toList)
        downstream.request(1)
      }

      downstream.cancel()
    }

    "provide info about number of messages" in {
      val sub = TestSubscriber.probe[Int]()
      val queue = Source.queue[Int](100).toMat(Sink.fromSubscriber(sub))(Keep.left).run()

      queue.offer(1)
      queue.size() shouldBe 1

      (2 to 100).map { i =>
        queue.offer(i)
      }
      queue.size() shouldBe 100
    }

    "can be used to create create method" in {
      Source.create[String](1024)(queue => {
        queue.offer("a")
        queue.offer("b")
        queue.complete()
      })
        .toMat(TestSink.probe[String])(Keep.right)
        .run()
        .ensureSubscription()
        .request(2)
        .expectNext("a", "b")
        .expectComplete()
    }
  }
}
