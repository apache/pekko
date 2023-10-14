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

import scala.collection.immutable
import scala.concurrent._
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl._

import scala.annotation.nowarn

object ZipWithIndexBenchmark {
  final val OperationsPerInvocation = 100000
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
@nowarn("msg=deprecated")
class ZipWithIndexBenchmark {
  import ZipWithIndexBenchmark._

  private val config = ConfigFactory.parseString("""
    akka.actor.default-dispatcher {
      executor = "fork-join-executor"
      fork-join-executor {
        parallelism-factor = 1
      }
    }
    """)

  private implicit val system: ActorSystem = ActorSystem("ZipWithIndexBenchmark", config)

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  private val newZipWithIndex = Source.repeat(1)
    .take(OperationsPerInvocation)
    .zipWithIndex.toMat(Sink.ignore)(Keep.right)

  private val oldZipWithIndex = Source
    .repeat(1)
    .take(OperationsPerInvocation)
    .statefulMapConcat[(Int, Long)] { () =>
      var index: Long = 0L
      elem => {
        val zipped = (elem, index)
        index += 1
        immutable.Iterable[(Int, Long)](zipped)
      }
    }
    .toMat(Sink.ignore)(Keep.right)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchOldZipWithIndex(): Unit =
    Await.result(oldZipWithIndex.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchNewZipWithIndex(): Unit =
    Await.result(newZipWithIndex.run(), Duration.Inf)

}
