/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.testkit

import java.io.{ PrintWriter, StringWriter }
import java.util.concurrent.CountDownLatch

import scala.annotation.tailrec
import scala.collection.immutable
import scala.concurrent.duration._
import scala.reflect.ClassTag

import org.apache.pekko
import pekko.actor.{
  ActorRef,
  ActorSystem,
  ClassicActorSystemProvider,
  DeadLetterSuppression,
  NoSerializationVerificationNeeded
}
import pekko.japi._
import pekko.stream._
import pekko.stream.impl._
import pekko.testkit.{ TestActor, TestProbe }
import pekko.testkit.TestActor.AutoPilot
import pekko.util.JavaDurationConverters._
import pekko.util.ccompat._
import pekko.util.ccompat.JavaConverters._

import org.reactivestreams.{ Publisher, Subscriber, Subscription }

/**
 * Provides factory methods for various Publishers.
 */
object TestPublisher {

  import StreamTestKit._

  trait PublisherEvent extends DeadLetterSuppression with NoSerializationVerificationNeeded
  final case class Subscribe(subscription: Subscription) extends PublisherEvent
  final case class CancelSubscription(subscription: Subscription, cause: Throwable) extends PublisherEvent
  final case class RequestMore(subscription: Subscription, elements: Long) extends PublisherEvent

  object SubscriptionDone extends NoSerializationVerificationNeeded

  /**
   * Publisher that signals complete to subscribers, after handing a void subscription.
   */
  def empty[T](): Publisher[T] = EmptyPublisher[T]

  /**
   * Publisher that subscribes the subscriber and completes after the first request.
   */
  def lazyEmpty[T]: Publisher[T] = new Publisher[T] {
    override def subscribe(subscriber: Subscriber[_ >: T]): Unit =
      subscriber.onSubscribe(CompletedSubscription(subscriber))
  }

  /**
   * Publisher that signals error to subscribers immediately after handing out subscription.
   */
  def error[T](cause: Throwable): Publisher[T] = ErrorPublisher(cause, "error").asInstanceOf[Publisher[T]]

  /**
   * Publisher that subscribes the subscriber and signals error after the first request.
   */
  def lazyError[T](cause: Throwable): Publisher[T] = new Publisher[T] {
    override def subscribe(subscriber: Subscriber[_ >: T]): Unit =
      subscriber.onSubscribe(FailedSubscription(subscriber, cause))
  }

  /**
   * Probe that implements [[org.reactivestreams.Publisher]] interface.
   */
  def manualProbe[T](autoOnSubscribe: Boolean = true)(implicit system: ActorSystem): ManualProbe[T] =
    new ManualProbe(autoOnSubscribe)

  /**
   * Probe that implements [[org.reactivestreams.Publisher]] interface and tracks demand.
   */
  def probe[T](initialPendingRequests: Long = 0)(implicit system: ActorSystem): Probe[T] =
    new Probe(initialPendingRequests)

  object ManualProbe {

    /**
     * Probe that implements [[org.reactivestreams.Publisher]] interface.
     */
    def apply[T](autoOnSubscribe: Boolean = true)(implicit system: ClassicActorSystemProvider): ManualProbe[T] =
      new ManualProbe(autoOnSubscribe)(system.classicSystem)

    /**
     * JAVA API
     *
     * Probe that implements [[org.reactivestreams.Publisher]] interface.
     * @since 1.1.0
     */
    def create[T](autoOnSubscribe: Boolean, system: ClassicActorSystemProvider): ManualProbe[T] =
      new ManualProbe(autoOnSubscribe)(system.classicSystem)
  }

  /**
   * Implementation of [[org.reactivestreams.Publisher]] that allows various assertions.
   * This probe does not track demand. Therefore you need to expect demand before sending
   * elements downstream.
   */
  class ManualProbe[I] private[TestPublisher] (autoOnSubscribe: Boolean = true)(implicit system: ActorSystem)
      extends Publisher[I] {

    type Self <: ManualProbe[I]

    @ccompatUsedUntil213
    private val probe: TestProbe = TestProbe()

    // this is a way to pause receiving message from probe until subscription is done
    private val subscribed = new CountDownLatch(1)
    probe.ignoreMsg { case SubscriptionDone => true }
    probe.setAutoPilot(new TestActor.AutoPilot() {
      override def run(sender: ActorRef, msg: Any): AutoPilot = {
        if (msg == SubscriptionDone) subscribed.countDown()
        this
      }
    })
    private val self = this.asInstanceOf[Self]

    /**
     * Subscribes a given [[org.reactivestreams.Subscriber]] to this probe publisher.
     */
    def subscribe(subscriber: Subscriber[_ >: I]): Unit = {
      val subscription: PublisherProbeSubscription[I] = new PublisherProbeSubscription[I](subscriber, probe)
      probe.ref ! Subscribe(subscription)
      if (autoOnSubscribe) subscriber.onSubscribe(subscription)
      probe.ref ! SubscriptionDone
    }

    def executeAfterSubscription[T](f: => T): T = {
      subscribed.await(
        probe.testKitSettings.DefaultTimeout.duration.length,
        probe.testKitSettings.DefaultTimeout.duration.unit)
      f
    }

    /**
     * JAVA API
     * @since 1.1.0
     */
    def executeAfterSubscription[T](f: function.Creator[T]): T = {
      executeAfterSubscription(f.create())
    }

    /**
     * Expect a subscription.
     */
    def expectSubscription(): PublisherProbeSubscription[I] =
      executeAfterSubscription {
        probe.expectMsgType[Subscribe].subscription.asInstanceOf[PublisherProbeSubscription[I]]
      }

    /**
     * Expect demand from a given subscription.
     */
    def expectRequest(subscription: Subscription, n: Int): Self = executeAfterSubscription {
      probe.expectMsg(RequestMore(subscription, n))
      self
    }

    /**
     * Expect no messages.
     * NOTE! Timeout value is automatically multiplied by timeFactor.
     */
    @deprecated(message = "Use expectNoMessage instead", since = "Akka 2.5.5")
    def expectNoMsg(): Self = executeAfterSubscription {
      probe.expectNoMsg()
      self
    }

    /**
     * Expect no messages for a given duration.
     * NOTE! Timeout value is automatically multiplied by timeFactor.
     */
    @deprecated(message = "Use expectNoMessage instead", since = "Akka 2.5.5")
    def expectNoMsg(max: FiniteDuration): Self = executeAfterSubscription {
      probe.expectNoMsg(max)
      self
    }

    /**
     * Expect no messages.
     * Waits for the default period configured as `pekko.actor.testkit.expect-no-message-default`.
     */
    def expectNoMessage(): Self = executeAfterSubscription {
      probe.expectNoMessage()
      self
    }

    /**
     * Expect no messages for a given duration.
     */
    def expectNoMessage(max: FiniteDuration): Self = executeAfterSubscription {
      probe.expectNoMessage(max)
      self
    }

    /**
     * JAVA API
     *
     * Expect no messages for a given duration.
     * @since 1.1.0
     */
    def expectNoMessage(max: java.time.Duration): Self = expectNoMessage(max.asScala)

    /**
     * Receive messages for a given duration or until one does not match a given partial function.
     */
    def receiveWhile[T](max: Duration = Duration.Undefined,
        idle: Duration = Duration.Inf,
        messages: Int = Int.MaxValue)(f: PartialFunction[PublisherEvent, T]): immutable.Seq[T] =
      executeAfterSubscription {
        probe.receiveWhile(max, idle, messages)(f.asInstanceOf[PartialFunction[AnyRef, T]])
      }

    /**
     * JAVA API
     *
     * Receive messages for a given duration or until one does not match a given partial function.
     * @since 1.1.0
     */
    def receiveWhile[T](max: java.time.Duration,
        idle: java.time.Duration,
        messages: Int,
        f: PartialFunction[PublisherEvent, T]): java.util.List[T] =
      receiveWhile(max.asScala, idle.asScala, messages)(f).asJava

    def expectEventPF[T](f: PartialFunction[PublisherEvent, T]): T =
      executeAfterSubscription {
        probe.expectMsgPF[T]()(f.asInstanceOf[PartialFunction[Any, T]])
      }

    def getPublisher: Publisher[I] = this

    /**
     * Execute code block while bounding its execution time between `min` and
     * `max`. `within` blocks may be nested. All methods in this trait which
     * take maximum wait times are available in a version which implicitly uses
     * the remaining time governed by the innermost enclosing `within` block.
     *
     * Note that the timeout is scaled using Duration.dilated, which uses the
     * configuration entry "pekko.test.timefactor", while the min Duration is not.
     *
     * {{{
     * val ret = within(50 millis) {
     *   test ! "ping"
     *   expectMsgClass(classOf[String])
     * }
     * }}}
     */
    def within[T](min: FiniteDuration, max: FiniteDuration)(f: => T): T = executeAfterSubscription {
      probe.within(min, max)(f)
    }

    /**
     * JAVA API
     *
     * Execute code block while bounding its execution time between `min` and
     * `max`. `within` blocks may be nested. All methods in this trait which
     * take maximum wait times are available in a version which implicitly uses
     * the remaining time governed by the innermost enclosing `within` block.
     *
     * Note that the timeout is scaled using Duration.dilated, which uses the
     * configuration entry "pekko.test.timefactor", while the min Duration is not.
     *
     * {{{
     * val ret = within(Duration.ofMillis(50)) {
     *   test ! "ping"
     *   expectMsgClass(classOf[String])
     * }
     * }}}
     *
     * @since 1.1.0
     */
    def within[T](min: java.time.Duration,
        max: java.time.Duration,
        creator: function.Creator[T]): T =
      within(min.asScala, max.asScala)(creator.create())

    /**
     * Same as calling `within(0 seconds, max)(f)`.
     */
    def within[T](max: FiniteDuration)(f: => T): T = executeAfterSubscription {
      probe.within(max)(f)
    }

    /**
     * JAVA API
     *
     * Same as calling `within(Duration.ofSeconds(0), max)(f)`.
     * @since 1.1.0
     */
    def within[T](max: java.time.Duration,
        creator: function.Creator[T]): T = within(max.asScala)(creator.create())
  }

  object Probe {
    def apply[T](initialPendingRequests: Long = 0)(implicit system: ClassicActorSystemProvider): Probe[T] =
      new Probe(initialPendingRequests)(system.classicSystem)

    /**
     * JAVA API
     * @since 1.1.0
     */
    def create[T](initialPendingRequests: Long, system: ClassicActorSystemProvider): Probe[T] =
      apply(initialPendingRequests)(system.classicSystem)
  }

  /**
   * Single subscription and demand tracking for [[TestPublisher.ManualProbe]].
   */
  class Probe[T] private[TestPublisher] (initialPendingRequests: Long)(implicit system: ActorSystem)
      extends ManualProbe[T] {

    type Self = Probe[T]

    private var pendingRequests = initialPendingRequests
    private lazy val subscription = expectSubscription()

    /** Asserts that a subscription has been received or will be received */
    def ensureSubscription(): Unit = subscription // initializes lazy val

    /**
     * Current pending requests.
     */
    def pending: Long = pendingRequests

    def sendNext(elem: T): Self = {
      if (pendingRequests == 0) pendingRequests = subscription.expectRequest()
      pendingRequests -= 1
      subscription.sendNext(elem)
      this
    }

    def unsafeSendNext(elem: T): Self = {
      subscription.sendNext(elem)
      this
    }

    def sendComplete(): Self = {
      subscription.sendComplete()
      this
    }

    def sendError(cause: Throwable): Self = {
      subscription.sendError(cause)
      this
    }

    def expectRequest(): Long = {
      val requests = subscription.expectRequest()
      pendingRequests += requests
      requests
    }

    def expectCancellation(): Self = {
      subscription.expectCancellation()
      this
    }

    def expectCancellationWithCause(expectedCause: Throwable): Self = {
      val cause = subscription.expectCancellation()
      assert(cause == expectedCause, s"Expected cancellation cause to be $expectedCause but was $cause")
      this
    }

    def expectCancellationWithCause[E <: Throwable: ClassTag](): E = subscription.expectCancellation() match {
      case e: E  => e
      case cause =>
        throw new AssertionError(
          s"Expected cancellation cause to be of type ${scala.reflect.classTag[E]} but was ${cause.getClass}: $cause")
    }

    /**
     * Java API
     */
    def expectCancellationWithCause[E <: Throwable](causeClass: Class[E]): E =
      expectCancellationWithCause()(ClassTag(causeClass))
  }

}

object TestSubscriber {

  trait SubscriberEvent extends DeadLetterSuppression with NoSerializationVerificationNeeded
  final case class OnSubscribe(subscription: Subscription) extends SubscriberEvent
  final case class OnNext[I](element: I) extends SubscriberEvent
  case object OnComplete extends SubscriberEvent
  final case class OnError(cause: Throwable) extends SubscriberEvent {
    override def toString: String = {
      val str = new StringWriter
      val out = new PrintWriter(str)
      out.print("OnError(")
      cause.printStackTrace(out)
      out.print(")")
      str.toString
    }
  }

  /**
   * Probe that implements [[org.reactivestreams.Subscriber]] interface.
   */
  def manualProbe[T]()(implicit system: ActorSystem): ManualProbe[T] = new ManualProbe()

  def probe[T]()(implicit system: ActorSystem): Probe[T] = new Probe()

  object ManualProbe {
    def apply[T]()(implicit system: ClassicActorSystemProvider): ManualProbe[T] =
      new ManualProbe()(system.classicSystem)

    /**
     * JAVA API
     * @since 1.1.0
     */
    def create[T]()(system: ClassicActorSystemProvider): ManualProbe[T] =
      apply()(system.classicSystem)
  }

  /**
   * Implementation of [[org.reactivestreams.Subscriber]] that allows various assertions.
   *
   * All timeouts are dilated automatically, for more details about time dilation refer to [[pekko.testkit.TestKit]].
   */
  class ManualProbe[I] private[TestSubscriber] ()(implicit system: ActorSystem) extends Subscriber[I] {
    import pekko.testkit._

    type Self <: ManualProbe[I]

    private val probe = TestProbe()

    @volatile private var _subscription: Subscription = _

    private val self = this.asInstanceOf[Self]

    /**
     * Expect and return a [[org.reactivestreams.Subscription]].
     */
    def expectSubscription(): Subscription = {
      _subscription = probe.expectMsgType[OnSubscribe].subscription
      _subscription
    }

    /**
     * Expect and return [[SubscriberEvent]] (any of: `OnSubscribe`, `OnNext`, `OnError` or `OnComplete`).
     */
    def expectEvent(): SubscriberEvent =
      probe.expectMsgType[SubscriberEvent]

    /**
     * Expect and return [[SubscriberEvent]] (any of: `OnSubscribe`, `OnNext`, `OnError` or `OnComplete`).
     */
    def expectEvent(max: FiniteDuration): SubscriberEvent =
      probe.expectMsgType[SubscriberEvent](max)

    /**
     * JAVA API
     *
     * Expect and return [[SubscriberEvent]] (any of: `OnSubscribe`, `OnNext`, `OnError` or `OnComplete`).
     * @since 1.1.0
     */
    def expectEvent(max: java.time.Duration): SubscriberEvent = expectEvent(max.asScala)

    /**
     * Fluent DSL
     *
     * Expect [[SubscriberEvent]] (any of: `OnSubscribe`, `OnNext`, `OnError` or `OnComplete`).
     */
    def expectEvent(event: SubscriberEvent): Self = {
      probe.expectMsg(event)
      self
    }

    /**
     * Expect and return a stream element.
     */
    def expectNext(): I = {
      expectNext(probe.testKitSettings.SingleExpectDefaultTimeout.dilated)
    }

    /**
     * Expect and return a stream element during specified time or timeout.
     */
    def expectNext(d: FiniteDuration): I = {
      val t = probe.remainingOr(d)
      probe.receiveOne(t) match {
        case null         => throw new AssertionError(s"Expected OnNext(_), yet no element signaled during $t")
        case OnNext(elem) => elem.asInstanceOf[I]
        case other        => throw new AssertionError("expected OnNext, found " + other)
      }
    }

    /**
     * JAVA API
     *
     * Expect and return a stream element during specified time or timeout.
     * @since 1.1.0
     */
    def expectNext(d: java.time.Duration): I = expectNext(d.asScala)

    /**
     * Fluent DSL
     *
     * Expect a stream element.
     */
    def expectNext(element: I): Self = {
      probe.expectMsg(OnNext(element))
      self
    }

    /**
     * Fluent DSL
     *
     * Expect a stream element during specified time or timeout.
     */
    def expectNext(d: FiniteDuration, element: I): Self = {
      probe.expectMsg(d, OnNext(element))
      self
    }

    /**
     * JAVA PAI
     *
     * Fluent DSL
     *
     * Expect a stream element during specified time or timeout.
     * @since 1.1.0
     */
    def expectNext(d: java.time.Duration, element: I): Self = expectNext(d.asScala, element)

    /**
     * Fluent DSL
     *
     * Expect multiple stream elements.
     */
    @annotation.varargs
    def expectNext(e1: I, e2: I, es: I*): Self =
      expectNextN((e1 +: e2 +: es).iterator.map(identity).to(immutable.IndexedSeq))

    /**
     * Fluent DSL
     *
     * Expect multiple stream elements in arbitrary order.
     */
    @annotation.varargs
    def expectNextUnordered(e1: I, e2: I, es: I*): Self =
      expectNextUnorderedN((e1 +: e2 +: es).iterator.map(identity).to(immutable.IndexedSeq))

    /**
     * Expect and return the next `n` stream elements.
     */
    def expectNextN(n: Long): immutable.Seq[I] = {
      val b = immutable.Seq.newBuilder[I]
      var i = 0
      while (i < n) {
        val next = probe.expectMsgType[OnNext[I]]
        b += next.element
        i += 1
      }

      b.result()
    }

    /**
     * Fluent DSL
     * Expect the given elements to be signalled in order.
     */
    def expectNextN(all: immutable.Seq[I]): Self = {
      all.foreach(e => probe.expectMsg(OnNext(e)))
      self
    }

    /**
     * Fluent DSL
     * Expect the given elements to be signalled in order.
     * @since 1.1.0
     */
    def expectNextN(elems: java.util.List[I]): Self = {
      elems.forEach(e => probe.expectMsg(OnNext(e)))
      self
    }

    /**
     * Fluent DSL
     * Expect the given elements to be signalled in any order.
     */
    def expectNextUnorderedN(all: immutable.Seq[I]): Self = {
      @annotation.tailrec
      def expectOneOf(all: immutable.Seq[I]): Unit = all match {
        case Nil =>
        case _   =>
          val next = expectNext()
          assert(all.contains(next), s"expected one of $all, but received $next")
          expectOneOf(all.diff(Seq(next)))
      }

      expectOneOf(all)
      self
    }

    /**
     * JAVA API
     *
     * Fluent DSL
     * Expect the given elements to be signalled in any order.
     * @since 1.1.0
     */
    def expectNextUnorderedN(all: java.util.List[I]): Self = expectNextUnorderedN(Util.immutableSeq(all))

    /**
     * Fluent DSL
     *
     * Expect completion.
     */
    def expectComplete(): Self = {
      probe.expectMsg(OnComplete)
      self
    }

    /**
     * Expect and return the signalled [[java.lang.Throwable]].
     */
    def expectError(): Throwable = probe.expectMsgType[OnError].cause

    /**
     * Fluent DSL
     *
     * Expect given [[java.lang.Throwable]].
     */
    def expectError(cause: Throwable): Self = {
      probe.expectMsg(OnError(cause))
      self
    }

    /**
     * Expect subscription to be followed immediately by an error signal.
     *
     * By default `1` demand will be signalled in order to wake up a possibly lazy upstream.
     *
     * See also [[#expectSubscriptionAndError(signalDemand:Boolean)* #expectSubscriptionAndError(signalDemand: Boolean)]] if no demand should be signalled.
     */
    def expectSubscriptionAndError(): Throwable = {
      expectSubscriptionAndError(true)
    }

    /**
     * Expect subscription to be followed immediately by an error signal.
     *
     * Depending on the `signalDemand` parameter demand may be signalled immediately after obtaining the subscription
     * in order to wake up a possibly lazy upstream. You can disable this by setting the `signalDemand` parameter to `false`.
     *
     * See also [[#expectSubscriptionAndError()* #expectSubscriptionAndError()]].
     */
    def expectSubscriptionAndError(signalDemand: Boolean): Throwable = {
      val sub = expectSubscription()
      if (signalDemand) sub.request(1)
      expectError()
    }

    /**
     * Fluent DSL
     *
     * Expect subscription followed by immediate stream completion.
     *
     * By default `1` demand will be signalled in order to wake up a possibly lazy upstream.
     *
     * See also [[#expectSubscriptionAndError(cause:Throwable,signalDemand:Boolean)* #expectSubscriptionAndError(cause: Throwable, signalDemand: Boolean)]] if no demand should be signalled.
     */
    def expectSubscriptionAndError(cause: Throwable): Self =
      expectSubscriptionAndError(cause, signalDemand = true)

    /**
     * Fluent DSL
     *
     * Expect subscription followed by immediate stream completion.
     * By default `1` demand will be signalled in order to wake up a possibly lazy upstream
     *
     * See also [[#expectSubscriptionAndError(cause:Throwable)* #expectSubscriptionAndError(cause: Throwable)]].
     */
    def expectSubscriptionAndError(cause: Throwable, signalDemand: Boolean): Self = {
      val sub = expectSubscription()
      if (signalDemand) sub.request(1)
      expectError(cause)
      self
    }

    /**
     * Fluent DSL
     *
     * Expect subscription followed by immediate stream completion.
     * By default `1` demand will be signalled in order to wake up a possibly lazy upstream
     *
     * See also [[#expectSubscriptionAndComplete(signalDemand:Boolean)* #expectSubscriptionAndComplete(signalDemand: Boolean)]] if no demand should be signalled.
     */
    def expectSubscriptionAndComplete(): Self =
      expectSubscriptionAndComplete(true)

    /**
     * Fluent DSL
     *
     * Expect subscription followed by immediate stream completion.
     *
     * Depending on the `signalDemand` parameter demand may be signalled immediately after obtaining the subscription
     * in order to wake up a possibly lazy upstream. You can disable this by setting the `signalDemand` parameter to `false`.
     *
     * See also [[#expectSubscriptionAndComplete()* #expectSubscriptionAndComplete]].
     */
    def expectSubscriptionAndComplete(signalDemand: Boolean): Self = {
      val sub = expectSubscription()
      if (signalDemand) sub.request(1)
      expectComplete()
      self
    }

    /**
     * Fluent DSL
     *
     * Expect given next element or error signal, returning whichever was signalled.
     */
    def expectNextOrError(): Either[Throwable, I] = {
      probe.fishForMessage(hint = s"OnNext(_) or error") {
        case OnNext(_)  => true
        case OnError(_) => true
      } match {
        case OnNext(n: I @unchecked) => Right(n)
        case OnError(err)            => Left(err)
        case _                       => throw new RuntimeException() // compiler exhaustiveness check pleaser
      }
    }

    /**
     * Fluent DSL
     * Expect given next element or error signal.
     */
    def expectNextOrError(element: I, cause: Throwable): Either[Throwable, I] = {
      probe.fishForMessage(hint = s"OnNext($element) or ${cause.getClass.getName}") {
        case OnNext(`element`) => true
        case OnError(`cause`)  => true
      } match {
        case OnNext(n: I @unchecked) => Right(n)
        case OnError(err)            => Left(err)
        case _                       => throw new RuntimeException() // compiler exhaustiveness check pleaser
      }
    }

    /**
     * Expect next element or stream completion - returning whichever was signalled.
     */
    def expectNextOrComplete(): Either[OnComplete.type, I] = {
      probe.fishForMessage(hint = s"OnNext(_) or OnComplete") {
        case OnNext(_)  => true
        case OnComplete => true
      } match {
        case OnComplete              => Left(OnComplete)
        case OnNext(n: I @unchecked) => Right(n)
        case _                       => throw new RuntimeException() // compiler exhaustiveness check pleaser
      }
    }

    /**
     * Fluent DSL
     *
     * Expect given next element or stream completion.
     */
    def expectNextOrComplete(element: I): Self = {
      probe.fishForMessage(hint = s"OnNext($element) or OnComplete") {
        case OnNext(`element`) => true
        case OnComplete        => true
      }
      self
    }

    /**
     * Fluent DSL
     *
     * Same as `expectNoMsg(remaining)`, but correctly treating the timeFactor.
     * NOTE! Timeout value is automatically multiplied by timeFactor.
     */
    @deprecated(message = "Use expectNoMessage instead", since = "Akka 2.5.5")
    def expectNoMsg(): Self = {
      probe.expectNoMsg()
      self
    }

    /**
     * Fluent DSL
     *
     * Assert that no message is received for the specified time.
     * NOTE! Timeout value is automatically multiplied by timeFactor.
     */
    @deprecated(message = "Use expectNoMessage instead", since = "Akka 2.5.5")
    def expectNoMsg(remaining: FiniteDuration): Self = {
      probe.expectNoMsg(remaining)
      self
    }

    /**
     * Fluent DSL
     *
     * Assert that no message is received for the specified time.
     */
    def expectNoMessage(remaining: FiniteDuration): Self = {
      probe.expectNoMessage(remaining)
      self
    }

    /**
     * Fluent DSL
     *
     * Assert that no message is received for the specified time.
     * Waits for the default period configured as `pekko.test.expect-no-message-default`.
     * That timeout is scaled using the configuration entry "pekko.test.timefactor".
     */
    def expectNoMessage(): Self = {
      probe.expectNoMessage()
      self
    }

    /**
     * Java API: Assert that no message is received for the specified time.
     */
    def expectNoMessage(remaining: java.time.Duration): Self = {
      probe.expectNoMessage(remaining.asScala)
      self
    }

    /**
     * Expect a stream element and test it with partial function.
     */
    def expectNextPF[T](f: PartialFunction[Any, T]): T =
      expectNextWithTimeoutPF(Duration.Undefined, f)

    /**
     * Expect a stream element and test it with partial function.
     *
     * @param max wait no more than max time, otherwise throw AssertionError
     */
    def expectNextWithTimeoutPF[T](max: Duration, f: PartialFunction[Any, T]): T = {
      val pf: PartialFunction[SubscriberEvent, T] = {
        case OnNext(n) if f.isDefinedAt(n) => f(n)
      }
      expectEventWithTimeoutPF[T](max, pf)
    }

    /**
     * JAVA API
     *
     * Expect a stream element and test it with partial function.
     *
     * @param max wait no more than max time, otherwise throw AssertionError
     * @since 1.1.0
     */
    def expectNextWithTimeoutPF[T](max: java.time.Duration, f: PartialFunction[Any, T]): T =
      expectEventWithTimeoutPF(max.asScala, f)

    /**
     * Expect a stream element during specified time or timeout and test it with partial function.
     *
     * Allows chaining probe methods.
     *
     * @param max wait no more than max time, otherwise throw AssertionError
     */
    def expectNextChainingPF(max: Duration, f: PartialFunction[Any, Any]): Self =
      expectNextWithTimeoutPF(max, f.andThen(_ => self))

    /**
     * JAVA API
     *
     * Expect a stream element during specified time or timeout and test it with partial function.
     *
     * Allows chaining probe methods.
     *
     * @param max wait no more than max time, otherwise throw AssertionError
     * @since 1.1.0
     */
    def expectNextChainingPF(max: java.time.Duration, f: PartialFunction[Any, Any]): Self =
      expectNextChainingPF(max.asScala, f)

    /**
     * Expect a stream element during specified time or timeout and test it with partial function.
     *
     * Allows chaining probe methods.
     */
    def expectNextChainingPF(f: PartialFunction[Any, Any]): Self =
      expectNextChainingPF(Duration.Undefined, f)

    def expectEventWithTimeoutPF[T](max: Duration, f: PartialFunction[SubscriberEvent, T]): T =
      probe.expectMsgPF[T](max, hint = "message matching partial function")(f.asInstanceOf[PartialFunction[Any, T]])

    /**
     * JAVA API
     * @since 1.1.0
     */
    def expectEventWithTimeoutPF[T](max: java.time.Duration, f: PartialFunction[SubscriberEvent, T]): T =
      expectEventWithTimeoutPF(max.asScala, f)

    def expectEventPF[T](f: PartialFunction[SubscriberEvent, T]): T =
      expectEventWithTimeoutPF(Duration.Undefined, f)

    /**
     * Receive messages for a given duration or until one does not match a given partial function.
     */
    def receiveWhile[T](
        max: Duration = Duration.Undefined,
        idle: Duration = Duration.Inf,
        messages: Int = Int.MaxValue)(f: PartialFunction[SubscriberEvent, T]): immutable.Seq[T] =
      probe.receiveWhile(max, idle, messages)(f.asInstanceOf[PartialFunction[AnyRef, T]])

    /**
     * JAVA API
     *
     * Receive messages for a given duration or until one does not match a given partial function.
     * @since 1.1.0
     */
    def receiveWhile[T](
        max: java.time.Duration,
        idle: java.time.Duration,
        messages: Int,
        f: PartialFunction[SubscriberEvent, T]): java.util.List[T] =
      receiveWhile(max.asScala, idle.asScala, messages)(f).asJava

    /**
     * Drains a given number of messages
     */
    def receiveWithin(max: FiniteDuration, messages: Int = Int.MaxValue): immutable.Seq[I] =
      probe
        .receiveWhile(max, max, messages) {
          case OnNext(i) => Some(i.asInstanceOf[I])
          case _         => None
        }
        .flatten

    /**
     * JAVA API
     *
     * Drains a given number of messages
     * @since 1.1.0
     */
    def receiveWithin(max: java.time.Duration, messages: Int): java.util.List[I] =
      receiveWithin(max.asScala, messages).asJava

    /**
     * Attempt to drain the stream into a strict collection (by requesting `Long.MaxValue` elements).
     *
     * '''Use with caution: Be warned that this may not be a good idea if the stream is infinite or its elements are very large!'''
     */
    def toStrict(atMost: FiniteDuration): immutable.Seq[I] = {
      val deadline = Deadline.now + atMost
      val b = immutable.Seq.newBuilder[I]

      @tailrec def drain(): immutable.Seq[I] =
        self.expectEvent(deadline.timeLeft) match {
          case OnError(ex) =>
            throw new AssertionError(
              s"toStrict received OnError while draining stream! Accumulated elements: ${b.result()}",
              ex)
          case OnComplete =>
            b.result()
          case OnNext(i: I @unchecked) =>
            b += i
            drain()
          case _ => throw new RuntimeException() // compiler exhaustiveness check pleaser
        }

      // if no subscription was obtained yet, we expect it
      if (_subscription == null) self.expectSubscription()
      _subscription.request(Long.MaxValue)

      drain()
    }

    /**
     * JAVA API
     *
     * Attempt to drain the stream into a strict collection (by requesting `Long.MaxValue` elements).
     *
     * '''Use with caution: Be warned that this may not be a good idea if the stream is infinite or its elements are very large!'''
     * @since 1.1.0
     */
    def toStrict(atMost: java.time.Duration): java.util.List[I] =
      toStrict(atMost.asScala).asJava

    /**
     * Execute code block while bounding its execution time between `min` and
     * `max`. `within` blocks may be nested. All methods in this trait which
     * take maximum wait times are available in a version which implicitly uses
     * the remaining time governed by the innermost enclosing `within` block.
     *
     * Note that the timeout is scaled using Duration.dilated, which uses the
     * configuration entry "pekko.test.timefactor", while the min Duration is not.
     *
     * {{{
     * val ret = within(50 millis) {
     *   test ! "ping"
     *   expectMsgClass(classOf[String])
     * }
     * }}}
     */
    def within[T](min: FiniteDuration, max: FiniteDuration)(f: => T): T = probe.within(min, max)(f)

    /**
     * JAVA API
     *
     * Execute code block while bounding its execution time between `min` and
     * `max`. `within` blocks may be nested. All methods in this trait which
     * take maximum wait times are available in a version which implicitly uses
     * the remaining time governed by the innermost enclosing `within` block.
     *
     * Note that the timeout is scaled using Duration.dilated, which uses the
     * configuration entry "pekko.test.timefactor", while the min Duration is not.
     *
     * {{{
     * val ret = within(Duration.ofMillis(50)) {
     *   test ! "ping"
     *   expectMsgClass(classOf[String])
     * }
     * }}}
     *
     * @since 1.1.0
     */
    def within[T](min: java.time.Duration,
        max: java.time.Duration,
        creator: function.Creator[T]): T = within(min.asScala, max.asScala)(creator.create())

    /**
     * Same as calling `within(0 seconds, max)(f)`.
     */
    def within[T](max: FiniteDuration)(f: => T): T = probe.within(max)(f)

    /**
     * JAVA API
     *
     * Same as calling `within(Duration.ofSeconds(0), max)(f)`.
     * @since 1.1.0
     */
    def within[T](max: java.time.Duration)(creator: function.Creator[T]): T = within(max.asScala)(creator.create())

    def onSubscribe(subscription: Subscription): Unit = probe.ref ! OnSubscribe(subscription)
    def onNext(element: I): Unit = probe.ref ! OnNext(element)
    def onComplete(): Unit = probe.ref ! OnComplete
    def onError(cause: Throwable): Unit = probe.ref ! OnError(cause)
  }

  object Probe {
    def apply[T]()(implicit system: ClassicActorSystemProvider): Probe[T] = new Probe()(system.classicSystem)

    /**
     * JAVA API
     *
     * @since 1.1.0
     */
    def create[T]()(implicit system: ClassicActorSystemProvider): Probe[T] = apply()(system)
  }

  /**
   * Single subscription tracking for [[ManualProbe]].
   */
  class Probe[T] private[TestSubscriber] ()(implicit system: ActorSystem) extends ManualProbe[T] {

    override type Self = Probe[T]

    private lazy val subscription = expectSubscription()

    /** Asserts that a subscription has been received or will be received */
    def ensureSubscription(): Self = {
      subscription // initializes lazy val
      this
    }

    def request(n: Long): Self = {
      subscription.request(n)
      this
    }

    /**
     * Request and expect a stream element.
     */
    def requestNext(element: T): Self = {
      subscription.request(1)
      expectNext(element)
      this
    }

    def cancel(): Self = {
      subscription.cancel()
      this
    }

    def cancel(cause: Throwable): Self = subscription match {
      case s: SubscriptionWithCancelException =>
        s.cancel(cause)
        this
      case _ =>
        throw new IllegalStateException(
          "Tried to cancel with cause but upstream subscription doesn't support cancellation with cause")
    }

    /**
     * Request and expect a stream element.
     */
    def requestNext(): T = {
      subscription.request(1)
      expectNext()
    }

    /**
     * Request and expect a stream element during the specified time or timeout.
     */
    def requestNext(d: FiniteDuration): T = {
      subscription.request(1)
      expectNext(d)
    }

    /**
     * JAVA API
     *
     * Request and expect a stream element during the specified time or timeout.
     * @since 1.1.0
     */
    def requestNext(d: java.time.Duration): T = requestNext(d.asScala)
  }
}

/**
 * INTERNAL API
 */
private[stream] object StreamTestKit {
  import TestPublisher._

  final case class CompletedSubscription[T](subscriber: Subscriber[T]) extends Subscription {
    override def request(elements: Long): Unit = subscriber.onComplete()
    override def cancel(): Unit = ()
  }

  final case class FailedSubscription[T](subscriber: Subscriber[T], cause: Throwable) extends Subscription {
    override def request(elements: Long): Unit = subscriber.onError(cause)
    override def cancel(): Unit = ()
  }

  final case class PublisherProbeSubscription[I](subscriber: Subscriber[_ >: I], publisherProbe: TestProbe)
      extends Subscription
      with SubscriptionWithCancelException {
    def request(elements: Long): Unit = publisherProbe.ref ! RequestMore(this, elements)
    def cancel(cause: Throwable): Unit = publisherProbe.ref ! CancelSubscription(this, cause)

    def expectRequest(n: Long): Unit = publisherProbe.expectMsg(RequestMore(this, n))
    def expectRequest(): Long = publisherProbe.expectMsgPF(hint = "expecting request() signal") {
      case RequestMore(sub, n) if sub eq this => n
    }

    def expectCancellation(): Throwable =
      publisherProbe.fishForSpecificMessage[Throwable](hint = "Expecting cancellation") {
        case CancelSubscription(sub, cause) if sub eq this => cause
      }

    def sendNext(element: I): Unit = subscriber.onNext(element)
    def sendComplete(): Unit = subscriber.onComplete()
    def sendError(cause: Throwable): Unit = subscriber.onError(cause)

    def sendOnSubscribe(): Unit = subscriber.onSubscribe(this)
  }

  final class ProbeSource[T](val attributes: Attributes, shape: SourceShape[T])(implicit system: ActorSystem)
      extends SourceModule[T, TestPublisher.Probe[T]](shape) {
    override def create(context: MaterializationContext) = {
      val probe = TestPublisher.probe[T]()
      (probe, probe)
    }
    override def withAttributes(attr: Attributes): SourceModule[T, TestPublisher.Probe[T]] =
      new ProbeSource[T](attr, amendShape(attr))
  }

  final class ProbeSink[T](val attributes: Attributes, shape: SinkShape[T])(implicit system: ActorSystem)
      extends SinkModule[T, TestSubscriber.Probe[T]](shape) {
    override def create(context: MaterializationContext) = {
      val probe = TestSubscriber.probe[T]()
      (probe, probe)
    }
    override def withAttributes(attr: Attributes): SinkModule[T, TestSubscriber.Probe[T]] =
      new ProbeSink[T](attr, amendShape(attr))
  }

}
