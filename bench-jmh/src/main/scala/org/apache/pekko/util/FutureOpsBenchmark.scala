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

package org.apache.pekko.util

import org.openjdk.jmh.annotations.{ Benchmark, BenchmarkMode, Measurement, Mode, OutputTimeUnit, Scope, State, Warmup }

import java.util.concurrent.TimeUnit
import scala.concurrent.Future

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1000)
@Measurement(iterations = 10000)
class FutureOpsBenchmark {
  private val completedFuture: Future[Int] = Future.successful(1)
  // jmh:run -i 11 -wi 11 -f1 -t1 org.apache.pekko.util.FutureOpsBenchmark
  //    [info] Benchmark                               Mode  Cnt       Score      Error   Units
  //    [info] FutureOpsBenchmark.awaitWithAwaitable  thrpt   11  706198.499 ± 8185.983  ops/ms
  //    [info] FutureOpsBenchmark.awaitWithFutureOps  thrpt   11  766901.781 ± 9741.792  ops/ms

  @Benchmark
  def awaitWithFutureOps(): Unit = {
    import scala.concurrent.duration._
    import org.apache.pekko.util.Helpers._
    completedFuture.await(Duration.Inf)
  }

  @Benchmark
  def awaitWithAwaitable(): Unit = {
    import scala.concurrent.duration._
    import scala.concurrent.Await
    Await.result(completedFuture, Duration.Inf)
  }
}
