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

package org.apache.pekko.stream

import java.util.concurrent.TimeUnit

import scala.concurrent._
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream.scaladsl._

object LazyFutureSourceBenchmark {
  final val OperationsPerInvocation = 100000
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class LazyFutureSourceBenchmark {
  import LazyFutureSourceBenchmark._

  private val config = ConfigFactory.parseString(
    """
  pekko.actor.default-dispatcher {
    executor = "fork-join-executor"
    fork-join-executor {
      parallelism-factor = 1
    }
  }
  """)

  implicit val system: ActorSystem = ActorSystem("LazyFutureSourceBenchmark", config)

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  private val newLazyFutureSource = Source.lazyFuture(() => Future.successful("")).toMat(Sink.ignore)(Keep.right)

  private val create = () => Future.successful("")
  private val oldLazyFutureSource = Source.lazySource { () =>
    val f = create()
    Source.future(f)
  }.mapMaterializedValue(_ => NotUsed)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def newLazyFuture(): Unit =
    Await.result(newLazyFutureSource.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def oldLazyFuture(): Unit =
    Await.result(oldLazyFutureSource.run(), Duration.Inf)
}
