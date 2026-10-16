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

package org.apache.pekko.dispatch

import java.util.concurrent.{
  CountDownLatch,
  ExecutorService,
  LinkedBlockingQueue,
  RejectedExecutionException,
  ThreadFactory,
  ThreadPoolExecutor,
  TimeUnit
}
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor._
import pekko.event.{ EventStream, Logging }
import pekko.event.Logging.Error
import pekko.testkit.{ PekkoSpec, TestProbe }

import com.typesafe.config.{ Config, ConfigFactory }

object RejectingExecutorConfigurator {

  /** Flipped by the spec to make every submission fail, the way a shut down or saturated executor does. */
  val rejecting = new AtomicBoolean(false)
  val pendingTasks = new AtomicInteger(0)
}

/**
 * An executor that can be switched into rejecting every task, so a spec can reproduce the
 * `RejectedExecutionException` that a dispatcher hits during shutdown without waiting for one.
 */
class RejectingExecutorConfigurator(
    @nowarn("msg=never used") config: Config,
    @nowarn("msg=never used") prerequisites: DispatcherPrerequisites)
    extends ExecutorServiceConfigurator(config, prerequisites) {

  override def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory =
    new ExecutorServiceFactory {
      override def createExecutorService: ExecutorService = {
        val underlying = new ThreadPoolExecutor(
          4,
          4,
          60L,
          TimeUnit.SECONDS,
          new LinkedBlockingQueue[Runnable](),
          threadFactory)
        new ExecutorServiceDelegate {
          override def executor: ExecutorService = underlying
          override def execute(command: Runnable): Unit =
            if (RejectingExecutorConfigurator.rejecting.get())
              throw new RejectedExecutionException("test executor is rejecting")
            else {
              RejectingExecutorConfigurator.pendingTasks.incrementAndGet()
              try executor.execute(() => {
                  try command.run()
                  finally RejectingExecutorConfigurator.pendingTasks.decrementAndGet()
                })
              catch {
                case e: RejectedExecutionException =>
                  RejectingExecutorConfigurator.pendingTasks.decrementAndGet()
                  throw e
              }
            }
        }
      }
    }
}

/**
 * Reporting a dispatch failure publishes to the EventStream, which delivers to a logging actor with
 * `!` and so goes back through dispatch. When the executor rejects, that re-enters the failure path
 * and recurses until the stack overflows, which `pekko.jvm-exit-on-fatal-error` turns into a JVM
 * kill. See https://github.com/apache/pekko/issues/3613.
 */
class DispatchFailureReportingSpec
    extends PekkoSpec(
      ConfigFactory
        .parseString(s"""
        pekko {
          jvm-exit-on-fatal-error = off
          loggers = ["org.apache.pekko.event.Logging$$DefaultLogger"]
          loglevel = "ERROR"
          actor.default-dispatcher.executor = "org.apache.pekko.dispatch.RejectingExecutorConfigurator"
        }
      """)
        .withFallback(PekkoSpec.testConf)) {

  private def echoActor(probe: TestProbe): ActorRef = {
    val ref = system.actorOf(Props(new Actor {
      override def receive: Receive = { case msg => probe.ref ! msg }
    }))
    // A reply can arrive while the mailbox is still Scheduled. Even after it becomes idle,
    // Mailbox.run can reschedule it, so wait until the executor has finished the whole task.
    ref ! "warmup"
    probe.expectMsg("warmup")
    awaitAssert(RejectingExecutorConfigurator.pendingTasks.get() shouldBe 0)
    ref
  }

  private def errorSubscriber(onError: => Unit): MinimalActorRef = new MinimalActorRef {
    override def path: ActorPath = RootActorPath(Address("pekko", "all-systems"), "/errorSubscriber")
    override def provider: ActorRefProvider = throw new UnsupportedOperationException("test ref")
    override def !(message: Any)(implicit sender: ActorRef): Unit = onError
  }

  "A message sent while the executor is rejecting" must {

    "not recurse into a StackOverflowError" in {
      val probe = TestProbe()
      val ref = echoActor(probe)
      val published = new AtomicInteger(0)
      val logger = echoActor(TestProbe())
      val counter = errorSubscriber {
        published.incrementAndGet()
        logger ! "log error"
      }
      val output = new java.io.ByteArrayOutputStream()
      // Forward through a synchronous ref so EventStreamUnsubscriber watches that ref rather
      // than asynchronously sending Watch to the idle actor whose dispatch must be rejected.
      system.eventStream.subscribe(counter, classOf[Logging.Error])
      awaitAssert(RejectingExecutorConfigurator.pendingTasks.get() shouldBe 0)

      RejectingExecutorConfigurator.rejecting.set(true)
      try {
        // Before the guard, the rejection report published to the logger actor, whose own dispatch
        // was rejected by the same executor, whose report published again, all on this thread.
        Console.withOut(output) { ref ! "hello" }
      } catch {
        case e: StackOverflowError =>
          fail("reporting the rejected execution recursed until the stack overflowed", e)
      } finally {
        RejectingExecutorConfigurator.rejecting.set(false)
        system.eventStream.unsubscribe(counter)
      }

      // The send above really was rejected and reported, so the assertion is not vacuous.
      published.get() should be > 0
      output.toString("UTF-8") should include("registerForExecution was rejected twice!")
    }

    "still dispatch normally once the executor accepts again" in {
      val rejectedProbe = TestProbe()
      val rejected = echoActor(rejectedProbe)

      RejectingExecutorConfigurator.rejecting.set(true)
      try rejected ! "hello"
      finally RejectingExecutorConfigurator.rejecting.set(false)

      // A fresh actor, so this does not depend on when the rejected message above is drained.
      val probe = TestProbe()
      val fresh = system.actorOf(Props(new Actor {
        override def receive: Receive = { case msg => probe.ref ! msg }
      }))

      fresh ! "delivered"
      probe.expectMsg("delivered")
    }
  }

  "DispatchFailureReporting" must {

    "send a nested failure to stdout even when it crosses event streams" in {
      val eventStream = new EventStream(system, false)
      val nestedEventStream = new EventStream(system, false)
      val deliveries = new AtomicInteger(0)
      val nestedDeliveries = new AtomicInteger(0)
      val output = new java.io.ByteArrayOutputStream()
      val reentrant = errorSubscriber {
        deliveries.incrementAndGet()
        DispatchFailureReporting.report(
          nestedEventStream,
          Error(Error.NoCause, "test", getClass, "nested failure"))
      }
      eventStream.subscribe(reentrant, classOf[Error])
      nestedEventStream.subscribe(errorSubscriber { nestedDeliveries.incrementAndGet(); () }, classOf[Error])

      Console.withOut(output) {
        DispatchFailureReporting.report(eventStream, Error(Error.NoCause, "test", getClass, "outer failure"))
        deliveries.get() shouldBe 1
        nestedDeliveries.get() shouldBe 0

        // A second outer report on this thread must reach the bus after the guard is released.
        DispatchFailureReporting.report(eventStream, Error(Error.NoCause, "test", getClass, "second outer failure"))
        deliveries.get() shouldBe 2
        nestedDeliveries.get() shouldBe 0
      }
      output.toString("UTF-8").linesIterator.count(_.contains("nested failure")) shouldBe 2
    }

    "release the guard when an EventStream subscriber throws" in {
      val eventStream = new EventStream(system, false)
      val failure = new IllegalStateException("subscriber failed")
      val subscriber = errorSubscriber { throw failure }
      eventStream.subscribe(subscriber, classOf[Error])
      val event = Error(Error.NoCause, "test", getClass, "outer failure")

      val caught = intercept[IllegalStateException] {
        DispatchFailureReporting.report(eventStream, event)
      }
      (caught should be).theSameInstanceAs(failure)

      eventStream.unsubscribe(subscriber)
      val deliveries = new AtomicInteger(0)
      eventStream.subscribe(errorSubscriber { deliveries.incrementAndGet(); () }, classOf[Error])
      DispatchFailureReporting.report(eventStream, event)
      deliveries.get() shouldBe 1
    }

    "not suppress reports that arrive concurrently from different threads" in {
      val eventStream = new EventStream(system, false)
      val threadCount = 16
      val entered = new CountDownLatch(threadCount)
      val proceed = new CountDownLatch(1)
      val done = new CountDownLatch(threadCount)
      val deliveries = new AtomicInteger(0)
      val subscriber = errorSubscriber {
        deliveries.incrementAndGet()
        entered.countDown()
        proceed.await()
      }
      eventStream.subscribe(subscriber, classOf[Error])
      val threads = (1 to threadCount).map { i =>
        val thread = new Thread(() => {
            try {
              DispatchFailureReporting.report(eventStream, Error(Error.NoCause, "test", getClass, "concurrent failure"))
            } catch {
              case _: InterruptedException => Thread.currentThread().interrupt()
            } finally done.countDown()
          }, s"dispatch-failure-reporter-$i")
        thread.setDaemon(true)
        thread
      }

      try {
        threads.foreach(_.start())
        // Keep every publish in flight until all threads have entered the subscriber. A shared
        // flag would divert all but one report and cannot satisfy this assertion.
        entered.await(remainingOrDefault.toMillis, TimeUnit.MILLISECONDS) shouldBe true
        deliveries.get() shouldBe threadCount
      } finally {
        // Release publishers even if starting a thread or an assertion fails.
        proceed.countDown()
        threads.foreach(_.join(remainingOrDefault.toMillis))
        eventStream.unsubscribe(subscriber)
      }
      done.getCount shouldBe 0L
      threads.foreach(_.isAlive shouldBe false)
    }
  }
}
