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

package org.apache.pekko.remote.artery

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration._

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue

import org.apache.pekko
import pekko.remote.artery.Association.LazyQueueWrapper
import pekko.remote.artery.Association.PendingQueueWrapper
import pekko.remote.artery.Association.QueueWrapperImpl
import pekko.remote.artery.Association.RestartGate
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.PekkoSpec
import pekko.testkit.TestException
import pekko.util.OptionVal

class AssociationQueueWrapperSpec extends PekkoSpec {

  private def envelope(msg: String): OutboundEnvelope = OutboundEnvelope(OptionVal.None, msg, OptionVal.None)

  private def newWrappers(): List[PendingQueueWrapper] =
    List(
      QueueWrapperImpl(new ManyToOneConcurrentArrayQueue[OutboundEnvelope](16)),
      new LazyQueueWrapper(() => new ManyToOneConcurrentArrayQueue[OutboundEnvelope](16), () => ()))

  /** Materializes a SendQueue for the wrapper's queue, like the outbound streams do when (re)started. */
  private def materialize(wrapper: PendingQueueWrapper)
      : (SendQueue.QueueValue[OutboundEnvelope], TestSubscriber.Probe[AnyRef]) = {
    val (queueValue, downstream) =
      Source
        .fromGraph(new SendQueue[OutboundEnvelope](_ => ()))
        .map(_.message)
        .toMat(TestSink[AnyRef]())(Keep.both)
        .run()
    downstream.request(10)
    queueValue.inject(wrapper.queue)
    // let the stage find the queue empty
    downstream.expectNoMessage(100.millis)
    (queueValue, downstream)
  }

  "A queue wrapper that has been replaced by the materialized SendQueue" must {

    "hand over offers from senders that still hold the wrapper" in {
      newWrappers().foreach { wrapper =>
        withClue(wrapper.getClass.getSimpleName) {
          val (queueValue, downstream) = materialize(wrapper)
          wrapper.handOver(queueValue)

          wrapper.offer(envelope("a")) shouldBe true
          downstream.expectNext("a")
          wrapper.offer(envelope("b")) shouldBe true
          downstream.expectNext("b")
          downstream.cancel()
        }
      }
    }

    "deliver a message that was offered to the wrapper after the stage polled the queue but before the hand over" in {
      newWrappers().foreach { wrapper =>
        withClue(wrapper.getClass.getSimpleName) {
          val (queueValue, downstream) = materialize(wrapper)

          wrapper.offer(envelope("a")) shouldBe true
          downstream.expectNoMessage(100.millis)

          wrapper.handOver(queueValue)
          downstream.expectNext("a")
          downstream.cancel()
        }
      }
    }
  }

  "A queue wrapper that has been replaced after a failed restart" must {

    def replace(previous: PendingQueueWrapper, requestRestart: () => Unit = () => ()): LazyQueueWrapper = {
      val next = new LazyQueueWrapper(() => previous.queue, requestRestart)
      previous.forwardTo(next)
      next
    }

    "wake up the retried stream for offers from senders that still hold the replaced wrapper" in {
      newWrappers().foreach { first =>
        withClue(first.getClass.getSimpleName) {
          // replaced twice, as when the restart failed twice
          val second = replace(first)
          val third = replace(second)
          val (queueValue, downstream) = materialize(third)
          third.handOver(queueValue)

          first.offer(envelope("a")) shouldBe true
          downstream.expectNext("a")
          second.offer(envelope("b")) shouldBe true
          downstream.expectNext("b")
          third.offer(envelope("c")) shouldBe true
          downstream.expectNext("c")
          downstream.cancel()
        }
      }
    }

    "request the retried restart for offers from senders that still hold the replaced wrapper" in {
      newWrappers().foreach { first =>
        withClue(first.getClass.getSimpleName) {
          val requests = new AtomicInteger
          val second = replace(first, () => requests.incrementAndGet())

          first.offer(envelope("a")) shouldBe true
          requests.get shouldBe 1
          second.queue.poll().message shouldBe "a"
        }
      }
    }

    "wake up the retried stream when it is replaced and handed over while a sender offers to the queue" in {
      // The sender has found the wrapper neither replaced nor handed over, and offers directly to the queue.
      // Meanwhile the restart fails, the wrapper is replaced, and the retried stream finds the queue empty.
      val queue = new ManyToOneConcurrentArrayQueue[OutboundEnvelope](16)
      val requests = new AtomicInteger
      var downstream: TestSubscriber.Probe[AnyRef] = null
      lazy val first: LazyQueueWrapper = new LazyQueueWrapper(
        () => queue,
        () => {
          // runs in the sender's offer, after the message has been enqueued
          val pending = queue.poll() // as if the retried stream had not seen the message
          val second = replace(first, () => requests.incrementAndGet())
          val (queueValue, probe) = materialize(second)
          downstream = probe
          second.handOver(queueValue)
          probe.expectNoMessage(100.millis)
          queue.offer(pending)
        })

      first.offer(envelope("a")) shouldBe true
      requests.get shouldBe 1
      downstream.expectNext("a")
      downstream.cancel()
    }
  }

  "A RestartGate" must {

    "start when requested after it has been opened" in {
      val starts = new AtomicInteger
      val gate = new RestartGate(() => starts.incrementAndGet())
      gate.open()
      starts.get shouldBe 0
      gate.request()
      starts.get shouldBe 1
    }

    "not start before it has been opened, and start when it is opened after a request" in {
      val starts = new AtomicInteger
      val gate = new RestartGate(() => starts.incrementAndGet())
      gate.request()
      gate.request()
      starts.get shouldBe 0
      gate.open()
      starts.get shouldBe 1
    }

    "start only once" in {
      val starts = new AtomicInteger
      val gate = new RestartGate(() => starts.incrementAndGet())
      gate.request()
      gate.open()
      gate.request()
      gate.open()
      starts.get shouldBe 1
    }

    "throw exceptions of the start to the caller that starts it" in {
      val gate = new RestartGate(() => throw TestException("start failed"))
      gate.request()
      intercept[TestException] {
        gate.open()
      }
    }

    "start exactly once when requested and opened concurrently" in {
      val requesters = 4
      (1 to 200).foreach { _ =>
        val starts = new AtomicInteger
        val gate = new RestartGate(() => starts.incrementAndGet())
        val ready = new CountDownLatch(1)
        val done = new CountDownLatch(requesters + 1)
        val actions = (() => gate.open()) :: List.fill(requesters)(() => gate.request())
        actions.foreach { action =>
          val t = new Thread(() => {
            ready.await()
            action()
            done.countDown()
          })
          t.setDaemon(true)
          t.start()
        }
        ready.countDown()
        done.await(10, TimeUnit.SECONDS) shouldBe true
        starts.get shouldBe 1
      }
    }
  }
}
