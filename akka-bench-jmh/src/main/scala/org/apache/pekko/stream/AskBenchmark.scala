/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.Props
import pekko.remote.artery.BenchTestSource
import pekko.remote.artery.LatchSink
import pekko.stream.scaladsl._
import pekko.stream.testkit.scaladsl.StreamTestKit
import pekko.util.Timeout

object AskBenchmark {
  final val OperationsPerInvocation = 100000
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class AskBenchmark {
  import AskBenchmark._

  val config = ConfigFactory.parseString("""
    akka.actor.default-dispatcher {
      executor = "fork-join-executor"
      fork-join-executor {
        parallelism-factor = 1
      }
    }
    """)

  implicit val system: ActorSystem = ActorSystem("MapAsyncBenchmark", config)
  import system.dispatcher

  var testSource: Source[java.lang.Integer, NotUsed] = _

  var actor: ActorRef = _

  implicit val timeout: Timeout = Timeout(10.seconds)

  @Param(Array("1", "4"))
  var parallelism = 0

  @Param(Array("false", "true"))
  var spawn = false

  @Setup
  def setup(): Unit = {
    testSource = Source.fromGraph(new BenchTestSource(OperationsPerInvocation))
    actor = system.actorOf(Props(new Actor {
      override def receive = {
        case element => sender() ! element
      }
    }))
    // eager init of materializer
    SystemMaterializer(system).materializer
  }

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def mapAsync(): Unit = {
    val latch = new CountDownLatch(1)

    testSource.ask[Int](parallelism)(actor).runWith(new LatchSink(OperationsPerInvocation, latch))

    awaitLatch(latch)
  }

  private def awaitLatch(latch: CountDownLatch): Unit = {
    if (!latch.await(30, TimeUnit.SECONDS)) {
      StreamTestKit.printDebugDump(SystemMaterializer(system).materializer.supervisor)
      throw new RuntimeException("Latch didn't complete in time")
    }
  }

}
