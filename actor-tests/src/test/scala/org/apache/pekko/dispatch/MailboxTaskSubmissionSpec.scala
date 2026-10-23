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

import java.util.concurrent.{ ForkJoinPool, ForkJoinTask, ForkJoinWorkerThread }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import org.apache.pekko
import pekko.dispatch.ForkJoinExecutorConfigurator.{ PekkoForkJoinPool, PekkoForkJoinTask }
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
 * This pins the mailbox's plain Runnable shape and resubmission after a failing run. Existing
 * ForkJoinTasks submitted as Runnables must retain their completion and cancellation semantics.
 */
class MailboxTaskSubmissionSpec extends PekkoSpec {

  private val quietHandler = new Thread.UncaughtExceptionHandler {
    override def uncaughtException(t: Thread, e: Throwable): Unit = ()
  }

  private final class QuietThreadFactory extends ForkJoinPool.ForkJoinWorkerThreadFactory {
    override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
      val thread = new ForkJoinWorkerThread(pool) {}
      thread.setName("MailboxTaskSubmissionSpec-worker")
      thread.setUncaughtExceptionHandler(quietHandler)
      thread
    }
  }

  /** Exposes a Runnable entry point so execute(Runnable) can receive an existing ForkJoinTask. */
  private final class RunnableForkJoinTask extends ForkJoinTask[Unit] with Runnable {
    val runs = new AtomicInteger(0)
    override def getRawResult(): Unit = ()
    override def setRawResult(unit: Unit): Unit = ()
    override def exec(): Boolean = {
      run()
      true
    }
    override def run(): Unit = {
      runs.incrementAndGet()
    }
  }

  private def newPool(): PekkoForkJoinPool =
    new PekkoForkJoinPool(2, new QuietThreadFactory, ForkJoinPoolConstants.MaxCap, quietHandler, asyncMode = true)

  "Mailbox" must {

    "not be a ForkJoinTask" in {
      classOf[ForkJoinTask[?]].isAssignableFrom(classOf[Mailbox]) shouldBe false
    }
  }

  "PekkoForkJoinTask" must {

    "preserve the Runnable hash code across fresh wrappers" in {
      for (hash <- Seq(Int.MinValue, -1, 0, 1, Int.MaxValue)) {
        val runnable = new Runnable {
          final override def run(): Unit = ()
          final override def hashCode(): Int = hash
        }
        val first = new PekkoForkJoinTask(runnable)
        val second = new PekkoForkJoinTask(runnable)

        withClue(s"Runnable hash code $hash: ") {
          first.hashCode() shouldBe hash
          second.hashCode() shouldBe hash
          (first should not).equal(second)
        }
      }
    }

    "retain independent completion and cancellation for wrappers with the same hash code" in {
      val runs = new AtomicInteger(0)
      val runnable = new Runnable {
        final override def run(): Unit = { runs.incrementAndGet() }
        final override def hashCode(): Int = 42
      }
      val cancelled = new PekkoForkJoinTask(runnable)
      val completed = new PekkoForkJoinTask(runnable)
      val pending = new PekkoForkJoinTask(runnable)

      cancelled.cancel(false) shouldBe true
      completed.invoke()

      cancelled.isCancelled shouldBe true
      completed.isCompletedNormally shouldBe true
      pending.isDone shouldBe false
      runs.get() shouldBe 1
      Set(cancelled, completed, pending).size shouldBe 3
      Seq(cancelled, completed, pending).foreach(_.hashCode() shouldBe 42)

      pending.invoke()
      pending.isCompletedNormally shouldBe true
      runs.get() shouldBe 2
    }
  }

  "PekkoForkJoinPool" must {

    "not run a cancelled ForkJoinTask submitted as a Runnable" in {
      val pool = newPool()
      val task = new RunnableForkJoinTask
      try {
        task.cancel(false) shouldBe true
        // Select execute(Runnable), which must preserve the existing task's cancellation state.
        pool.execute(task: Runnable)
      } finally pool.shutdown()
      pool.awaitTermination(remainingOrDefault.toMillis, java.util.concurrent.TimeUnit.MILLISECONDS) shouldBe true
      task.isCancelled shouldBe true
      task.runs.get() shouldBe 0
    }

    "complete a ForkJoinTask submitted as a Runnable" in {
      val pool = newPool()
      val task = new RunnableForkJoinTask
      try {
        pool.execute(task: Runnable)
        // Bound the wait so a regression fails instead of hanging in join().
        awaitAssert(task.isDone shouldBe true)
        task.isCompletedNormally shouldBe true
        task.join()
        task.runs.get() shouldBe 1
      } finally pool.shutdown()
    }

    // Control: plain Runnables receive a fresh wrapper on every submission, including before #3621.
    "run a plain reused Runnable again after one of its runs threw" in {
      val pool = newPool()
      try {
        val runs = new AtomicInteger(0)
        val firstRunFailed = new AtomicBoolean(false)
        val task = new Runnable {
          override def run(): Unit = {
            runs.incrementAndGet()
            if (firstRunFailed.compareAndSet(false, true)) throw new RuntimeException("expected test failure")
          }
        }

        pool.execute(task)
        awaitAssert(runs.get() shouldBe 1)
        // Let the first wrapper finish failing before checking a subsequent submission.
        awaitAssert(pool.isQuiescent shouldBe true)
        pool.execute(task)
        awaitAssert(runs.get() shouldBe 2)
      } finally pool.shutdown()
    }
  }
}
