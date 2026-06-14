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
import scala.concurrent.Promise

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.impl.LinearTraversalBuilder
import org.apache.pekko.stream.impl.Stages.DefaultAttributes
import org.apache.pekko.stream.impl.fusing.GraphStages
import org.apache.pekko.stream.impl.fusing.IterableSource
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.stage.GraphStageWithMaterializedValue

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class GraphStageConstructionBenchmark {
  private val element = "element"
  private val elements: immutable.Iterable[String] = Vector(element, element)
  private val range = 1 to 1000
  private val pendingFuture = Promise[String]().future
  private val flowStage = GraphStages.identity[Any]
  private val sinkStage = GraphStages.IgnoreSink

  private def oldSourceFromGraphStage[T, M](
      stage: GraphStageWithMaterializedValue[SourceShape[T], M]): Source[T, M] = {
    val attributes = stage.traversalBuilder.attributes
    val noAttributeStage = stage.withAttributes(Attributes.none)
    new Source(
      LinearTraversalBuilder.fromBuilder(noAttributeStage.traversalBuilder, noAttributeStage.shape, Keep.right),
      noAttributeStage.shape).withAttributes(attributes)
  }

  private def oldSourceFromIterable[T](iterable: immutable.Iterable[T]): Source[T, NotUsed] =
    (iterable.knownSize: @scala.annotation.switch) match {
      case 0 => Source.empty
      case 1 => oldSourceFromGraphStage(new GraphStages.SingleSource(iterable.head))
      case _ =>
        oldSourceFromGraphStage(new IterableSource[T](iterable)).withAttributes(DefaultAttributes.iterableSource)
    }

  @Benchmark
  def sourceSingle(blackhole: Blackhole): Unit =
    blackhole.consume(Source.single(element))

  @Benchmark
  def javadslSourceSingle(blackhole: Blackhole): Unit =
    blackhole.consume(javadsl.Source.single(element))

  @Benchmark
  def sourceRepeat(blackhole: Blackhole): Unit =
    blackhole.consume(Source.repeat(element))

  @Benchmark
  def oldSourceRepeatPath(blackhole: Blackhole): Unit = {
    val iterable = new immutable.Iterable[String] {
      override def iterator: Iterator[String] = Iterator.continually(element)
      override def toString: String = "() => Iterator"
    }
    blackhole.consume(
      oldSourceFromGraphStage(new IterableSource[String](iterable)).withAttributes(DefaultAttributes.repeat))
  }

  @Benchmark
  def sourceFromIterator(blackhole: Blackhole): Unit =
    blackhole.consume(Source.fromIterator(() => Iterator.single(element)))

  @Benchmark
  def oldSourceFromIteratorPath(blackhole: Blackhole): Unit = {
    val iterable = new immutable.Iterable[String] {
      override def iterator: Iterator[String] = Iterator.single(element)
      override def toString: String = "() => Iterator"
    }
    blackhole.consume(
      oldSourceFromGraphStage(new IterableSource[String](iterable)).withAttributes(DefaultAttributes.iterableSource))
  }

  @Benchmark
  def sourceIterable(blackhole: Blackhole): Unit =
    blackhole.consume(Source(elements))

  @Benchmark
  def oldSourceIterablePath(blackhole: Blackhole): Unit =
    blackhole.consume(oldSourceFromIterable(elements))

  @Benchmark
  def sourceRange(blackhole: Blackhole): Unit =
    blackhole.consume(Source(range))

  @Benchmark
  def oldSourceRangePath(blackhole: Blackhole): Unit =
    blackhole.consume(
      oldSourceFromGraphStage(new IterableSource[Int](range)).withAttributes(DefaultAttributes.iterableSource))

  @Benchmark
  def javadslSourceRange(blackhole: Blackhole): Unit =
    blackhole.consume(javadsl.Source.range(1, 1000))

  @Benchmark
  def oldJavadslSourceRangePath(blackhole: Blackhole): Unit =
    blackhole.consume(
      new javadsl.Source(
        oldSourceFromGraphStage(new IterableSource[Integer](range.asInstanceOf[immutable.Iterable[Integer]]))
          .withAttributes(DefaultAttributes.iterableSource)))

  @Benchmark
  def sourceFuturePending(blackhole: Blackhole): Unit =
    blackhole.consume(Source.future(pendingFuture))

  @Benchmark
  def oldSourceFuturePendingPath(blackhole: Blackhole): Unit =
    blackhole.consume(oldSourceFromGraphStage(new GraphStages.FutureSource[String](pendingFuture)))

  @Benchmark
  def sourceFromGraphStage(blackhole: Blackhole): Unit =
    blackhole.consume(Source.fromGraph(new GraphStages.SingleSource(element)))

  @Benchmark
  def oldSourceFromGraphStagePath(blackhole: Blackhole): Unit = {
    val stage = new GraphStages.SingleSource(element)
    blackhole.consume(oldSourceFromGraphStage(stage))
  }

  @Benchmark
  def sinkFromGraphStage(blackhole: Blackhole): Unit =
    blackhole.consume(Sink.fromGraph(sinkStage))

  @Benchmark
  def oldSinkFromGraphStagePath(blackhole: Blackhole): Unit = {
    val attributes = sinkStage.traversalBuilder.attributes
    val noAttributeStage = sinkStage.withAttributes(Attributes.none)
    blackhole.consume(
      new Sink(
        LinearTraversalBuilder.fromBuilder(noAttributeStage.traversalBuilder, noAttributeStage.shape, Keep.right),
        noAttributeStage.shape).withAttributes(attributes))
  }

  @Benchmark
  def flowFromGraphStage(blackhole: Blackhole): Unit =
    blackhole.consume(Flow.fromGraph(flowStage))

  @Benchmark
  def oldFlowFromGraphStagePath(blackhole: Blackhole): Unit = {
    val attributes = flowStage.traversalBuilder.attributes
    val noAttributeStage = flowStage.withAttributes(Attributes.none)
    blackhole.consume(
      new Flow(
        LinearTraversalBuilder.fromBuilder(noAttributeStage.traversalBuilder, noAttributeStage.shape, Keep.right),
        noAttributeStage.shape).withAttributes(attributes))
  }
}
