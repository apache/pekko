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

import java.util.concurrent.{ ForkJoinPool, ForkJoinTask, ForkJoinWorkerThread, TimeUnit }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import org.apache.pekko
import pekko.dispatch.ForkJoinExecutorConfigurator.PekkoForkJoinPool
import pekko.testkit.PekkoSpec

/**
 * A Mailbox is one object that its dispatcher resubmits for the actor's whole lifetime, and
 * `ForkJoinPool.execute` pushes a `ForkJoinTask` as-is instead of wrapping it. Letting the mailbox
 * itself reach the pool therefore puts the same reference back into the same work-queue slot over
 * and over, which a steal that stalls between its base recheck and its slot CAS turns into a
 * permanently damaged queue, and which one exceptional completion turns into a mailbox that never
 * runs again. See https://github.com/ben-manes/caffeine/issues/2021 and
 * https://github.com/apache/pekko/issues/3620.
 *
 * The work-queue race cannot be forced deterministically. The exceptional-completion regression
 * verifies that a reused `ForkJoinTask` submitted as a `Runnable` remains executable after a failed
 * run. In 1.x, Mailbox retains its ForkJoinTask superclass for binary compatibility; the executor
 * must wrap each submission in a fresh task.
 */
class MailboxTaskSubmissionSpec extends PekkoSpec {

  protected val quietHandler = new Thread.UncaughtExceptionHandler {
    override def uncaughtException(t: Thread, e: Throwable): Unit = ()
  }

  protected final class QuietThreadFactory extends ForkJoinPool.ForkJoinWorkerThreadFactory {
    override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
      val thread = new ForkJoinWorkerThread(pool) {}
      thread.setName("MailboxTaskSubmissionSpec-worker")
      thread.setUncaughtExceptionHandler(quietHandler)
      thread
    }
  }

  /** The shape of `Mailbox`: resubmitted, `exec()` never completes it normally, and rethrows. */
  private final class ReusedTask(body: Runnable) extends ForkJoinTask[Unit] with Runnable {
    val runs = new AtomicInteger(0)
    override def getRawResult(): Unit = ()
    override def setRawResult(unit: Unit): Unit = ()
    override def exec(): Boolean = {
      run()
      false
    }
    override def run(): Unit = {
      runs.incrementAndGet()
      body.run()
    }
  }

  protected def newPool(): ForkJoinPool =
    new PekkoForkJoinPool(2, new QuietThreadFactory, quietHandler, asyncMode = true)

  "Fork-join executor" must {

    // The mailbox is submitted through ExecutorService.execute(Runnable), so cast to keep the
    // overload resolution identical to the dispatcher's call site.
    "run a reused ForkJoinTask again after one of its runs threw" in {
      val pool = newPool()
      try {
        val firstRunFailed = new AtomicBoolean(false)
        val task = new ReusedTask(new Runnable {
          override def run(): Unit =
            if (firstRunFailed.compareAndSet(false, true)) throw new RuntimeException("expected test failure")
        })

        pool.execute(task: Runnable)
        awaitAssert(task.runs.get() shouldBe 1)

        // Wait for exceptional completion before resubmitting the task.
        awaitAssert(pool.isQuiescent shouldBe true)

        pool.execute(task: Runnable)
        // Before the fix the task object itself was pushed, the throw completed it exceptionally,
        // and ForkJoinTask.doExec short-circuits on a negative status, so this stayed at 1.
        awaitAssert(task.runs.get() shouldBe 2)
      } finally {
        pool.shutdown()
        pool.awaitTermination(remainingOrDefault.toMillis, TimeUnit.MILLISECONDS) shouldBe true
      }
    }

    "still run a plain reused Runnable" in {
      val pool = newPool()
      try {
        val runs = new AtomicInteger(0)
        val task = new Runnable {
          override def run(): Unit = runs.incrementAndGet()
        }

        pool.execute(task)
        awaitAssert(runs.get() shouldBe 1)
        pool.execute(task)
        awaitAssert(runs.get() shouldBe 2)
      } finally {
        pool.shutdown()
        pool.awaitTermination(remainingOrDefault.toMillis, TimeUnit.MILLISECONDS) shouldBe true
      }
    }
  }
}
