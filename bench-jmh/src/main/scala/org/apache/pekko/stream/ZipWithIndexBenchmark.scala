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

import scala.annotation.nowarn
import scala.collection.immutable
import scala.concurrent._
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.{ GatherCollector, Gatherer, Keep, OneToOneGatherer, Sink, Source }

import com.typesafe.config.ConfigFactory

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
    pekko.actor.default-dispatcher {
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

  private val statefulMapZipWithIndex = Source
    .repeat(1)
    .take(OperationsPerInvocation)
    .statefulMap(() => 0L)((index, elem) => (index + 1, (elem, index)), _ => None)
    .toMat(Sink.ignore)(Keep.right)

  private val gatherPublicZipWithIndex = Source
    .repeat(1)
    .take(OperationsPerInvocation)
    .gather(() =>
      new Gatherer[Int, (Int, Long)] {
        private var index = 0L

        override def apply(elem: Int, collector: GatherCollector[(Int, Long)]): Unit = {
          val zipped = (elem, index)
          index += 1
          collector.push(zipped)
        }
      })
    .toMat(Sink.ignore)(Keep.right)

  private val gatherInternalOneToOneZipWithIndex = Source
    .repeat(1)
    .take(OperationsPerInvocation)
    .gather(() =>
      new OneToOneGatherer[Int, (Int, Long)] {
        private var index = 0L

        override def applyOne(elem: Int): (Int, Long) = {
          val zipped = (elem, index)
          index += 1
          zipped
        }
      })
    .toMat(Sink.ignore)(Keep.right)

  private val statefulMapIncrement = Source
    .repeat(1)
    .take(OperationsPerInvocation)
    .statefulMap(() => ())((state, elem) => (state, elem + 1), _ => None)
    .toMat(Sink.ignore)(Keep.right)

  private val gatherPublicIncrement = Source
    .repeat(1)
    .take(OperationsPerInvocation)
    .gather(() =>
      new Gatherer[Int, Int] {
        override def apply(elem: Int, collector: GatherCollector[Int]): Unit =
          collector.push(elem + 1)
      })
    .toMat(Sink.ignore)(Keep.right)

  private val gatherInternalOneToOneIncrement = Source
    .repeat(1)
    .take(OperationsPerInvocation)
    .gather(() =>
      new OneToOneGatherer[Int, Int] {
        override def applyOne(elem: Int): Int = elem + 1
      })
    .toMat(Sink.ignore)(Keep.right)

  private val statefulMapCountedIncrement = Source
    .repeat(1)
    .take(OperationsPerInvocation)
    .statefulMap(() => 0L)((index, elem) => (index + 1, elem + index.toInt), _ => None)
    .toMat(Sink.ignore)(Keep.right)

  private val gatherPublicCountedIncrement = Source
    .repeat(1)
    .take(OperationsPerInvocation)
    .gather(() =>
      new Gatherer[Int, Int] {
        private var index = 0L

        override def apply(elem: Int, collector: GatherCollector[Int]): Unit = {
          val incremented = elem + index.toInt
          index += 1
          collector.push(incremented)
        }
      })
    .toMat(Sink.ignore)(Keep.right)

  private val gatherInternalOneToOneCountedIncrement = Source
    .repeat(1)
    .take(OperationsPerInvocation)
    .gather(() =>
      new OneToOneGatherer[Int, Int] {
        private var index = 0L

        override def applyOne(elem: Int): Int = {
          val incremented = elem + index.toInt
          index += 1
          incremented
        }
      })
    .toMat(Sink.ignore)(Keep.right)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchOldZipWithIndex(): Unit =
    Await.result(oldZipWithIndex.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchNewZipWithIndex(): Unit =
    Await.result(newZipWithIndex.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchStatefulMapZipWithIndex(): Unit =
    Await.result(statefulMapZipWithIndex.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchGatherPublicZipWithIndex(): Unit =
    Await.result(gatherPublicZipWithIndex.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchGatherInternalOneToOneZipWithIndex(): Unit =
    Await.result(gatherInternalOneToOneZipWithIndex.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchStatefulMapIncrement(): Unit =
    Await.result(statefulMapIncrement.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchGatherPublicIncrement(): Unit =
    Await.result(gatherPublicIncrement.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchGatherInternalOneToOneIncrement(): Unit =
    Await.result(gatherInternalOneToOneIncrement.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchStatefulMapCountedIncrement(): Unit =
    Await.result(statefulMapCountedIncrement.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchGatherPublicCountedIncrement(): Unit =
    Await.result(gatherPublicCountedIncrement.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchGatherInternalOneToOneCountedIncrement(): Unit =
    Await.result(gatherInternalOneToOneCountedIncrement.run(), Duration.Inf)

}
