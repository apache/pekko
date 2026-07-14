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

import java.util
import java.util.concurrent.{ AbstractExecutorService, CountDownLatch, ExecutorService, Executors, TimeUnit }
import java.util.concurrent.atomic.AtomicBoolean

import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.testkit.{ ImplicitSender, PekkoSpec }

import com.typesafe.config.ConfigFactory

object ForkJoinPoolVirtualThreadSpec {
  val config = ConfigFactory.parseString("""
      |custom {
      |  task-dispatcher {
      |    mailbox-type = "org.apache.pekko.dispatch.SingleConsumerOnlyUnboundedMailbox"
      |    throughput = 5
      |    executor = "fork-join-executor"
      |    fork-join-executor {
      |      parallelism-factor = 2
      |      parallelism-max = 2
      |      parallelism-min = 2
      |      virtualize = on
      |      virtual-thread-start-number = 0
      |    }
      |  }
      |  task-dispatcher-short {
      |    mailbox-type = "org.apache.pekko.dispatch.SingleConsumerOnlyUnboundedMailbox"
      |    throughput = 5
      |    executor = "fork-join-executor"
      |    fork-join-executor {
      |      parallelism-factor = 2
      |      parallelism-max = 2
      |      parallelism-min = 2
      |      virtualize = on
      |      virtual-thread-start-number = -1
      |    }
      |  }
      |}
    """.stripMargin)

  class ThreadNameActor extends Actor {

    override def receive = {
      case "ping" =>
        sender() ! Thread.currentThread().getName
    }
  }

  final class TrackingExecutorService(delegate: ExecutorService) extends AbstractExecutorService {
    val shutdownCalled = new AtomicBoolean(false)
    val shutdownNowCalled = new AtomicBoolean(false)

    override def shutdown(): Unit = {
      shutdownCalled.set(true)
      delegate.shutdown()
    }

    override def shutdownNow(): util.List[Runnable] = {
      shutdownNowCalled.set(true)
      delegate.shutdownNow()
    }

    override def isShutdown: Boolean = delegate.isShutdown

    override def isTerminated: Boolean = delegate.isTerminated

    override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
      delegate.awaitTermination(timeout, unit)
    }

    override def execute(command: Runnable): Unit = {
      delegate.execute(command)
    }
  }

}

class ForkJoinPoolVirtualThreadSpec extends PekkoSpec(ForkJoinPoolVirtualThreadSpec.config) with ImplicitSender {
  import ForkJoinPoolVirtualThreadSpec._

  "PekkoForkJoinPool" must {

    "support virtualization with Virtual Thread" in {
      val actor = system.actorOf(Props(new ThreadNameActor).withDispatcher("custom.task-dispatcher"))
      for (_ <- 1 to 1000) {
        actor ! "ping"
        expectMsgPF() { case name: String =>
          name should include("ForkJoinPoolVirtualThreadSpec-custom.task-dispatcher-virtual-thread-")
        }
      }

      val actor2 = system.actorOf(Props(new ThreadNameActor).withDispatcher("custom.task-dispatcher-short"))
      for (_ <- 1 to 1000) {
        actor2 ! "ping"
        expectMsgPF() { case name: String =>
          name should include("ForkJoinPoolVirtualThreadSpec-custom.task-dispatcher-short-virtual-thread")
        }
      }
    }

    "allow blocked virtual threads to finish after dispatcher shutdown" in {
      val underlying = new TrackingExecutorService(Executors.newFixedThreadPool(1))
      val executor = new VirtualizedExecutorService(
        VirtualThreadSupport.newVirtualThreadFactory("fork-join-pool-virtual-thread-spec", 0, underlying),
        underlying,
        _ => false,
        cascadeShutdown = true)

      val started = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val finished = new CountDownLatch(1)

      try {
        executor.execute(() => {
          started.countDown()
          release.await()
          finished.countDown()
        })

        started.await(5, TimeUnit.SECONDS) should ===(true)
        executor.shutdown()
        underlying.isShutdown should ===(false)

        release.countDown()
        finished.await(5, TimeUnit.SECONDS) should ===(true)
        executor.awaitTermination(5, TimeUnit.SECONDS) should ===(true)
        underlying.shutdownCalled.get should ===(true)
        underlying.shutdownNowCalled.get should ===(false)
        underlying.isTerminated should ===(true)
      } finally {
        release.countDown()
        executor.shutdownNow()
        underlying.shutdownNow()
      }
    }

    "interrupt blocked virtual threads on dispatcher shutdownNow" in {
      val underlying = new TrackingExecutorService(Executors.newFixedThreadPool(1))
      val executor = new VirtualizedExecutorService(
        VirtualThreadSupport.newVirtualThreadFactory("fork-join-pool-virtual-thread-spec", 0, underlying),
        underlying,
        _ => false,
        cascadeShutdown = true)

      val started = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val interrupted = new CountDownLatch(1)

      try {
        executor.execute(() => {
          started.countDown()
          try release.await()
          catch {
            case _: InterruptedException => interrupted.countDown()
          }
        }
        )

        started.await(5, TimeUnit.SECONDS) should ===(true)
        executor.shutdownNow()

        interrupted.await(5, TimeUnit.SECONDS) should ===(true)
        executor.awaitTermination(5, TimeUnit.SECONDS) should ===(true)
        underlying.shutdownNowCalled.get should ===(true)
        underlying.isTerminated should ===(true)
      } finally {
        release.countDown()
        executor.shutdownNow()
        underlying.shutdownNow()
      }
    }

    "escalate to underlying shutdownNow after dispatcher shutdown" in {
      val underlying = new TrackingExecutorService(Executors.newFixedThreadPool(1))
      val executor = new VirtualizedExecutorService(
        VirtualThreadSupport.newVirtualThreadFactory("fork-join-pool-virtual-thread-spec", 0, underlying),
        underlying,
        _ => false,
        cascadeShutdown = true)

      val started = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val interrupted = new CountDownLatch(1)

      try {
        executor.execute(() => {
          started.countDown()
          try release.await()
          catch {
            case _: InterruptedException => interrupted.countDown()
          }
        })

        started.await(5, TimeUnit.SECONDS) should ===(true)
        executor.shutdown()
        underlying.isShutdown should ===(false)

        executor.shutdownNow()
        interrupted.await(5, TimeUnit.SECONDS) should ===(true)
        executor.awaitTermination(5, TimeUnit.SECONDS) should ===(true)
        underlying.shutdownNowCalled.get should ===(true)
        underlying.isTerminated should ===(true)
      } finally {
        release.countDown()
        executor.shutdownNow()
        underlying.shutdownNow()
      }
    }

  }
}
