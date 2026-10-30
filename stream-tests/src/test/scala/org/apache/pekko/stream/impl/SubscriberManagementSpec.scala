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

package org.apache.pekko.stream.impl

import org.reactivestreams.{ Subscriber, Subscription }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SubscriberManagementSpec extends AnyWordSpec with Matchers {

  "SubscriberManagement" should {

    "honor successive requests with a single subscriber and a one-element buffer" in {
      val publisher = new TestPublisher(1, 1)
      val subscriber = publisher.subscribe()

      subscriber.request(1)
      subscriber.request(1)
      publisher.requests shouldBe Vector(1L)
      publisher.send(1)
      publisher.send(2)
      publisher.requests shouldBe Vector(1L, 1L)
      subscriber.elements shouldBe Vector(1, 2)
    }

    "account for outstanding upstream requests when readers add demand and free buffer space" in {
      val publisher = new TestPublisher(2, 4)
      val fast = publisher.subscribe()
      val slow = publisher.subscribe()

      fast.request(16)
      fast.request(16)
      slow.request(1)
      publisher.requests shouldBe Vector(4L)

      publisher.send(1)
      publisher.requests shouldBe Vector(4L, 1L)
      publisher.send(2)
      fast.request(1)
      publisher.requests shouldBe Vector(4L, 1L)
      fast.elements shouldBe Vector(1, 2)
      slow.elements shouldBe Vector(1)

      slow.request(1)
      slow.elements shouldBe Vector(1, 2)
      publisher.requests shouldBe Vector(4L, 1L, 1L)
    }

    "use the greatest demand when all subscribers request less than the available capacity" in {
      val publisher = new TestPublisher(2, 8)
      val first = publisher.subscribe()
      val second = publisher.subscribe()
      val third = publisher.subscribe()

      first.request(2)
      second.request(1)
      third.request(4)
      publisher.requests shouldBe Vector(2L, 2L)

      publisher.send(1)
      publisher.send(2)
      publisher.requests shouldBe Vector(2L, 2L)
      first.elements shouldBe Vector(1, 2)
      second.elements shouldBe Vector(1)
      third.elements shouldBe Vector(1, 2)

      third.request(1)
      second.request(1)
      publisher.requests shouldBe Vector(2L, 2L, 1L)
      second.elements shouldBe Vector(1, 2)
    }

    "recompute demand after the subscriber with the greatest demand cancels" in {
      val publisher = new TestPublisher(2, 4)
      val fast = publisher.subscribe()
      val remaining = publisher.subscribe()

      fast.request(4)
      remaining.request(1)
      fast.cancel()
      publisher.send(1)
      remaining.request(2)
      publisher.send(2)
      publisher.send(3)
      publisher.requests shouldBe Vector(4L)

      remaining.request(2)
      publisher.requests shouldBe Vector(4L, 1L)
      remaining.elements shouldBe Vector(1, 2, 3)
      publisher.cancelled shouldBe false
    }

    "resume unbounded demand after the reader holding a full buffer cancels" in {
      val publisher = new TestPublisher(1, 4)
      val fast = publisher.subscribe()
      val slow = publisher.subscribe()

      fast.request(Long.MaxValue)
      (1 to 4).foreach(publisher.send)
      fast.request(Long.MaxValue)
      publisher.requests shouldBe Vector(4L)
      fast.elements shouldBe Vector(1, 2, 3, 4)
      slow.elements shouldBe empty

      slow.cancel()
      publisher.requests shouldBe Vector(4L, 4L)
      publisher.send(5)
      publisher.requests shouldBe Vector(4L, 4L, 1L)
      fast.elements shouldBe Vector(1, 2, 3, 4, 5)
    }

    "request exactly one upstream element for each round of single-element subscriber demand" in {
      val publisher = new TestPublisher(2, 8)
      val subscribers = Vector.fill(4)(publisher.subscribe())

      for (element <- 1 to 10) {
        val order = if (element % 2 == 0) subscribers.reverse else subscribers
        order.foreach(_.request(1))
        publisher.requests shouldBe Vector.fill(element)(1L)
        publisher.send(element)
        publisher.requests shouldBe Vector.fill(element)(1L)
        subscribers.foreach(_.elements shouldBe (1 to element).toVector)
      }
    }

    "preserve outstanding demand when a new reader joins a partially filled buffer" in {
      val publisher = new TestPublisher(1, 4)
      val fast = publisher.subscribe()
      val slow = publisher.subscribe()
      fast.request(Long.MaxValue)
      publisher.send(1)

      val joining = publisher.subscribe()
      joining.request(2)
      joining.elements shouldBe Vector(1)
      publisher.requests shouldBe Vector(4L)

      slow.request(1)
      publisher.send(2)
      joining.elements shouldBe Vector(1, 2)
      publisher.requests shouldBe Vector(4L, 1L)

      slow.cancel()
      publisher.requests shouldBe Vector(4L, 1L, 1L)
      fast.elements shouldBe Vector(1, 2)
    }

    "drain a completed upstream to existing and late readers without requesting more" in {
      val publisher = new TestPublisher(1, 4)
      val fast = publisher.subscribe()
      val slow = publisher.subscribe()
      fast.request(Long.MaxValue)
      publisher.send(1)
      publisher.send(2)
      publisher.complete()
      fast.completions shouldBe 1

      val joining = publisher.subscribe()
      joining.request(1)
      joining.elements shouldBe Vector(1)
      joining.completions shouldBe 0
      slow.request(2)
      joining.request(1)

      slow.elements shouldBe Vector(1, 2)
      joining.elements shouldBe Vector(1, 2)
      slow.completions shouldBe 1
      joining.completions shouldBe 1
      publisher.requests shouldBe Vector(4L)
    }
  }

  private class TestPublisher(override val initialBufferSize: Int, override val maxBufferSize: Int)
      extends SubscriberManagement[Int] {
    override type S = SubscriptionWithCursor[Int]

    var requests = Vector.empty[Long]
    var cancelled = false
    private var outstanding = 0L

    def subscribe(): RecordingSubscriber = {
      val subscriber = new RecordingSubscriber
      registerSubscriber(subscriber)
      subscriber
    }

    def send(element: Int): Unit = {
      require(outstanding > 0, "No outstanding upstream demand")
      outstanding -= 1
      pushToDownstream(element)
    }

    def complete(): Unit = completeDownstream()

    override protected def requestFromUpstream(elements: Long): Unit = {
      requests :+= elements
      outstanding += elements
    }

    override protected def cancelUpstream(): Unit = cancelled = true
    override protected def shutdown(completed: Boolean): Unit = ()

    override protected def createSubscription(target: Subscriber[? >: Int]): S =
      new SubscriptionWithCursor[Int] {
        override def subscriber: Subscriber[? >: Int] = target
        override def request(elements: Long): Unit = moreRequested(this, elements)
        override def cancel(): Unit = unregisterSubscription(this)
      }
  }

  private class RecordingSubscriber extends Subscriber[Int] {
    private var subscription: Subscription = _
    var elements = Vector.empty[Int]
    var completions = 0

    def request(elements: Long): Unit = subscription.request(elements)
    def cancel(): Unit = subscription.cancel()

    override def onSubscribe(subscription: Subscription): Unit = this.subscription = subscription
    override def onNext(element: Int): Unit = elements :+= element
    override def onComplete(): Unit = completions += 1
    override def onError(cause: Throwable): Unit = throw new AssertionError("Unexpected subscriber failure", cause)
  }
}
