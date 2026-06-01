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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.NoSerializationVerificationNeeded
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.GraphStageWithMaterializedValue
import pekko.stream.stage.InHandler

object StageActorRefBenchmark {
  final val OperationsPerInvocation = 10000
  private case object CountDown extends NoSerializationVerificationNeeded

  private final class Control {
    private val ready = new CountDownLatch(1)
    @volatile private var ref: ActorRef = _
    @volatile private var latch: CountDownLatch = _

    def init(ref: ActorRef): Unit = {
      this.ref = ref
      ready.countDown()
    }

    def stageActorRef: ActorRef = {
      if (!ready.await(10, TimeUnit.SECONDS))
        throw new RuntimeException("Stage actor ref was not initialized")
      ref
    }

    def reset(expectedMessages: Int): Unit =
      latch = new CountDownLatch(expectedMessages)

    def countDown(): Unit =
      latch.countDown()

    def awaitDone(): Unit =
      if (!latch.await(10, TimeUnit.SECONDS))
        throw new RuntimeException("Stage actor ref benchmark messages timed out")
  }

  private final class StageActorSink extends GraphStageWithMaterializedValue[SinkShape[Any], Control] {
    val in: Inlet[Any] = Inlet("StageActorSink.in")
    override val shape: SinkShape[Any] = SinkShape(in)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Control) = {
      val control = new Control

      val logic = new GraphStageLogic(shape) {
        override def preStart(): Unit = {
          control.init(getStageActor {
            case (_, CountDown) => control.countDown()
          }.ref)
          pull(in)
        }

        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit = pull(in)
          })
      }

      logic -> control
    }
  }
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class StageActorRefBenchmark {
  import StageActorRefBenchmark._

  implicit val system: ActorSystem = ActorSystem("StageActorRefBenchmark")

  private var completion: Promise[Option[Any]] = _
  private var control: Control = _
  private var stageActorRef: ActorRef = _

  @Setup
  def setup(): Unit = {
    SystemMaterializer(system).materializer
    val materialized = Source.maybe[Any].toMat(Sink.fromGraph(new StageActorSink))(Keep.both).run()
    completion = materialized._1
    control = materialized._2
    stageActorRef = control.stageActorRef
  }

  @TearDown
  def shutdown(): Unit = {
    completion.trySuccess(None)
    Await.result(system.terminate(), 5.seconds)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def lazy_stage_actor_ref_tell_10k(): Unit = {
    control.reset(OperationsPerInvocation)
    var remaining = OperationsPerInvocation
    while (remaining > 0) {
      stageActorRef ! CountDown
      remaining -= 1
    }
    control.awaitDone()
  }
}
