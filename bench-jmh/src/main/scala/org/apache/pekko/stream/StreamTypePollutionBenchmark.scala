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

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.Done
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.RunnableGraph
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(3)
@Threads(12)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
class StreamTypePollutionBenchmark {
  import StreamTypePollutionBenchmark._

  private var system: ActorSystem = _
  private var materializer: Materializer = _
  private var concreteGraph: RunnableGraph[Future[Done]] = _
  private var singleInterfaceGraph: RunnableGraph[Future[Done]] = _
  private var alternatingInterfacesGraph: RunnableGraph[Future[Done]] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    system = ActorSystem(
      "StreamTypePollutionBenchmark",
      ConfigFactory.parseString(s"""
        pekko {
          log-dead-letters = off
          actor.default-dispatcher.fork-join-executor {
            parallelism-min = 12
            parallelism-factor = 1.0
            parallelism-max = 12
          }
          stream.materializer.sync-processing-limit = ${Int.MaxValue}
        }
      """))
    materializer = SystemMaterializer(system).materializer

    val payloads = Vector.tabulate[Payload](ElementCount) { i =>
      (i & 3) match {
        case 0 => new Payload0
        case 1 => new Payload1
        case 2 => new Payload2
        case _ => new Payload3
      }
    }
    val leftPayloads: Vector[Left] = payloads

    concreteGraph =
      Source(payloads)
        .map(KeepPayload)
        .map(KeepPayload)
        .map(KeepPayload)
        .map(KeepPayload)
        .map(KeepPayload)
        .map(KeepPayload)
        .toMat(Sink.ignore)(Keep.right)

    singleInterfaceGraph =
      Source(leftPayloads)
        .map(KeepLeft)
        .map(KeepLeft)
        .map(KeepLeft)
        .map(KeepLeft)
        .map(KeepLeft)
        .map(KeepLeft)
        .toMat(Sink.ignore)(Keep.right)

    alternatingInterfacesGraph =
      Source(leftPayloads)
        .map(ToRight)
        .map(ToLeft)
        .map(ToRight)
        .map(ToLeft)
        .map(ToRight)
        .map(ToLeft)
        .toMat(Sink.ignore)(Keep.right)
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit =
    system.close()

  @Benchmark
  @OperationsPerInvocation(ElementCount)
  def concreteElements(): Done =
    Await.result(concreteGraph.run()(materializer), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(ElementCount)
  def singleInterfaceElements(): Done =
    Await.result(singleInterfaceGraph.run()(materializer), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(ElementCount)
  def alternatingInterfaceElements(): Done =
    Await.result(alternatingInterfacesGraph.run()(materializer), Duration.Inf)
}

object StreamTypePollutionBenchmark {
  final val ElementCount = 100000

  trait Left {
    def toRight: Right
  }

  trait Right {
    def toLeft: Left
  }

  abstract class Payload extends Left with Right {
    final override def toRight: Right = this
    final override def toLeft: Left = this
  }

  final class Payload0 extends Payload
  final class Payload1 extends Payload
  final class Payload2 extends Payload
  final class Payload3 extends Payload

  object KeepPayload extends (Payload => Payload) {
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    override def apply(payload: Payload): Payload = payload
  }

  object KeepLeft extends (Left => Left) {
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    override def apply(payload: Left): Left = payload
  }

  object ToRight extends (Left => Right) {
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    override def apply(payload: Left): Right = payload.toRight
  }

  object ToLeft extends (Right => Left) {
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    override def apply(payload: Right): Left = payload.toLeft
  }
}
