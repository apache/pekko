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

package org.apache.pekko.actor

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(3)
@Threads(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
class ReceiveTimeoutBenchmark {
  import ReceiveTimeoutBenchmark._

  private var system: ActorSystem = _
  private var target: ActorRef = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    system = ActorSystem(
      "ReceiveTimeoutBenchmark",
      ConfigFactory.parseString("""
        pekko {
          log-dead-letters = off
          scheduler.tick-duration = 1 ms
          actor.default-dispatcher {
            fork-join-executor {
              parallelism-min = 1
              parallelism-factor = 1.0
              parallelism-max = 1
            }
            throughput = 1000
          }
        }
      """))
    target = system.actorOf(Props[TimeoutActor]())
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit =
    system.close()

  @Benchmark
  @OperationsPerInvocation(MessageCount)
  def influencingMessages(): Unit = {
    val latch = new CountDownLatch(1)
    var i = 0
    while (i < MessageCount) {
      target ! Message
      i += 1
    }
    target ! new Finished(latch)
    latch.await()
  }
}

object ReceiveTimeoutBenchmark {
  final val MessageCount = 10000

  case object Message

  final class Finished(val latch: CountDownLatch) extends NotInfluenceReceiveTimeout

  final class TimeoutActor extends Actor {
    context.setReceiveTimeout(100.millis)

    override def receive: Receive = {
      case Message            =>
      case finished: Finished => finished.latch.countDown()
      case ReceiveTimeout     =>
    }
  }
}
