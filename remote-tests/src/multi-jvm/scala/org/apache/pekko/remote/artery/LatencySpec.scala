/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.locks.LockSupport

import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.HdrHistogram.Histogram

import org.apache.pekko
import pekko.actor._
import pekko.dispatch.Dispatchers
import pekko.remote.RemotingMultiNodeSpec
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.serialization.jackson.CborSerializable
import pekko.stream.ThrottleMode
import pekko.stream.scaladsl.Source
import pekko.testkit._

import com.typesafe.config.ConfigFactory

object LatencySpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  val barrierTimeout = 5.minutes

  commonConfig(debugConfig(on = false).withFallback(ConfigFactory.parseString(s"""
       # for serious measurements you should increase the totalMessagesFactor (30) and repeatCount (3)
       org.apache.pekko.test.LatencySpec.totalMessagesFactor = 1.0
       org.apache.pekko.test.LatencySpec.repeatCount = 1
       org.apache.pekko.test.LatencySpec.real-message = off
       pekko {
         loglevel = ERROR
         # avoid TestEventListener
         loggers = ["org.apache.pekko.event.Logging$$DefaultLogger"]
         testconductor.barrier-timeout = ${barrierTimeout.toSeconds}s
         actor {
           provider = remote
           serializers.test-message = "org.apache.pekko.remote.artery.TestMessageSerializer"
           serialization-bindings {
             "org.apache.pekko.remote.artery.TestMessage" = test-message
           }
         }
         remote.artery {
           enabled = on
           advanced.aeron.idle-cpu-level = 7

           advanced.inbound-lanes = 1

           # for serious measurements when running this test on only one machine
           # it is recommended to use external media driver
           # See remote/src/test/resources/aeron.properties
           # advanced.aeron.embedded-media-driver = off
           # advanced.aeron.aeron-dir = "remote/target/aeron"
           # on linux, use directory on ram disk, instead
           # advanced.aeron.aeron-dir = "/dev/shm/aeron"

           advanced.compression {
             actor-refs.advertisement-interval = 2 second
             manifests.advertisement-interval = 2 second
           }
         }
       }
       """)).withFallback(RemotingMultiNodeSpec.commonConfig))

  case object Reset extends CborSerializable

  def echoProps(): Props =
    Props(new Echo).withDispatcher(Dispatchers.InternalDispatcherId)

  class Echo extends Actor {
    // FIXME to avoid using new RemoteActorRef each time
    var cachedSender: ActorRef = null

    def receive = {
      case Reset =>
        cachedSender = null
        sender() ! Reset
      case msg =>
        if (cachedSender == null) cachedSender = sender()
        cachedSender ! msg
    }
  }

  def receiverProps(
      reporter: RateReporter,
      settings: TestSettings,
      totalMessages: Int,
      measurements: LatencyMeasurements,
      plotsRef: ActorRef,
      BenchmarkFileReporter: BenchmarkFileReporter): Props =
    Props(new Receiver(reporter, settings, totalMessages, measurements, plotsRef, BenchmarkFileReporter))
      .withDispatcher(Dispatchers.InternalDispatcherId)

  class Receiver(
      reporter: RateReporter,
      settings: TestSettings,
      totalMessages: Int,
      measurements: LatencyMeasurements,
      plotsRef: ActorRef,
      BenchmarkFileReporter: BenchmarkFileReporter)
      extends Actor {
    import settings._

    var startTime = 0L

    def receive = {
      case bytes: Array[Byte] =>
        if (bytes.length != 0) {
          // Raw byte payloads have no sequence number; this mode relies on ordered delivery.
          receiveMessage(measurements.receivedCount, bytes.length)
        }
      case message: TestMessage =>
        receiveMessage(message.id, message.payload.length)
    }

    def receiveMessage(id: Long, size: Int): Unit = try {
      require(size == payloadSize, "Invalid payload size")
      val now = System.nanoTime()
      if (measurements.receivedCount == 0)
        startTime = now
      measurements.recordEcho(id, now)
      reporter.onMessage(1, size)
      if (measurements.receivedCount == totalMessages) {
        printTotal(testName, measurements.roundTrip, now - startTime, BenchmarkFileReporter)
        reportDistribution("Scheduled-to-echo", measurements.scheduledToEcho)
        reportDistribution("Send lateness", measurements.sendLateness)
        context.stop(self)
      }
    } catch {
      case NonFatal(ex) =>
        // Do not restart and eventually publish a partial/invalid round as successful.
        plotsRef ! Status.Failure(ex)
        context.stop(self)
    }

    def reportDistribution(label: String, histogram: Histogram): Unit = {
      def percentile(p: Double): Double = histogram.getValueAtPercentile(p) / 1000.0
      BenchmarkFileReporter.reportResults(
        s"=== ${BenchmarkFileReporter.testName} $testName: $label " +
        f"50%%ile: ${percentile(50.0)}%.0f µs, " +
        f"90%%ile: ${percentile(90.0)}%.0f µs, " +
        f"99%%ile: ${percentile(99.0)}%.0f µs")
    }

    def printTotal(
        testName: String,
        histogram: Histogram,
        totalDurationNanos: Long,
        reporter: BenchmarkFileReporter): Unit = {
      def percentile(p: Double): Double = histogram.getValueAtPercentile(p) / 1000.0
      val throughput = 1e9 * (histogram.getTotalCount - 1) / math.max(1L, totalDurationNanos)

      reporter.reportResults(
        s"=== ${reporter.testName} $testName: RTT " +
        f"50%%ile: ${percentile(50.0)}%.0f µs, " +
        f"90%%ile: ${percentile(90.0)}%.0f µs, " +
        f"99%%ile: ${percentile(99.0)}%.0f µs, " +
        f"rate: $throughput%,.0f msg/s")
      println("Histogram of RTT latencies in microseconds.")
      histogram.outputPercentileDistribution(System.out, 1000.0)

      val plots = LatencyPlots(
        PlotResult().add(testName, percentile(50.0)),
        PlotResult().add(testName, percentile(90.0)),
        PlotResult().add(testName, percentile(99.0)))
      plotsRef ! plots
    }
  }

  final case class TestSettings(
      testName: String,
      messageRate: Int, // msg/s
      payloadSize: Int,
      repeat: Int,
      realMessage: Boolean)

}

class LatencySpecMultiJvmNode1 extends LatencySpec
class LatencySpecMultiJvmNode2 extends LatencySpec

abstract class LatencySpec extends RemotingMultiNodeSpec(LatencySpec) {

  import LatencySpec._

  val totalMessagesFactor = system.settings.config.getDouble("org.apache.pekko.test.LatencySpec.totalMessagesFactor")
  val repeatCount = system.settings.config.getInt("org.apache.pekko.test.LatencySpec.repeatCount")
  val realMessage = system.settings.config.getBoolean("org.apache.pekko.test.LatencySpec.real-message")

  var plots = LatencyPlots()

  import system.dispatcher

  override def initialParticipants = roles.size

  lazy val reporterExecutor = Executors.newFixedThreadPool(1)
  def reporter(name: String): TestRateReporter = {
    val r = new TestRateReporter(name)
    reporterExecutor.execute(r)
    r
  }

  override def afterAll(): Unit = {
    reporterExecutor.shutdown()
    runOn(first) {
      println(plots.plot50.csv(system.name + "50"))
      println(plots.plot90.csv(system.name + "90"))
      println(plots.plot99.csv(system.name + "99"))
    }
    super.afterAll()
  }

  def identifyEcho(name: String = "echo", r: RoleName = second): ActorRef = {
    system.actorSelection(node(r) / "user" / name) ! Identify(None)
    expectMsgType[ActorIdentity].ref.get
  }

  val scenarios = List(
    TestSettings(testName = "warmup", messageRate = 10000, payloadSize = 100, repeat = repeatCount, realMessage),
    TestSettings(
      testName = "rate-100-size-100",
      messageRate = 100,
      payloadSize = 100,
      repeat = repeatCount,
      realMessage),
    TestSettings(
      testName = "rate-1000-size-100",
      messageRate = 1000,
      payloadSize = 100,
      repeat = repeatCount,
      realMessage),
    TestSettings(
      testName = "rate-10000-size-100",
      messageRate = 10000,
      payloadSize = 100,
      repeat = repeatCount,
      realMessage),
    TestSettings(
      testName = "rate-20000-size-100",
      messageRate = 20000,
      payloadSize = 100,
      repeat = repeatCount,
      realMessage),
    TestSettings(
      testName = "rate-1000-size-1k",
      messageRate = 1000,
      payloadSize = 1000,
      repeat = repeatCount,
      realMessage))

  def test(testSettings: TestSettings, BenchmarkFileReporter: BenchmarkFileReporter): Unit = {
    import testSettings._

    runOn(first) {
      val payload = ("0" * payloadSize).getBytes(StandardCharsets.UTF_8)
      // by default run for 2 seconds, but can be adjusted with the totalMessagesFactor
      val totalMessages = (2 * messageRate * totalMessagesFactor).toInt
      require(totalMessages >= 2, "LatencySpec requires at least two messages per round")
      val echo = identifyEcho()
      val plotProbe = TestProbe()
      val rep = reporter(testName)

      try for (n <- 1 to repeat) {
          echo ! Reset
          expectMsg(Reset)
          val measurements = new LatencyMeasurements(totalMessages)
          val receiver = system.actorOf(
            receiverProps(rep, testSettings, totalMessages, measurements, plotProbe.ref, BenchmarkFileReporter))

          // warmup for 3 seconds to init compression
          val warmup = Source(1 to 30).throttle(10, 1.second, 10, ThrottleMode.Shaping).runForeach { _ =>
            echo.tell(Array.emptyByteArray, receiver)
          }

          val sending = warmup.map { _ =>
            val schedule = new LatencyMeasurements.SendSchedule(System.nanoTime(), messageRate)
            var i = 0
            while (i < totalMessages) {
              if (Thread.currentThread().isInterrupted) throw new IllegalStateException("Latency generator interrupted")
              val planned = schedule.deadline(i)
              var remaining = planned - System.nanoTime()
              // parkNanos may wake spuriously. If late, catch up without moving the schedule.
              while (remaining > 0) {
                if (Thread.currentThread().isInterrupted)
                  throw new IllegalStateException("Latency generator interrupted")
                LockSupport.parkNanos(remaining)
                remaining = planned - System.nanoTime()
              }

              // Preserve the RTT start point before message construction and tell.
              measurements.recordSend(i, planned, System.nanoTime())
              val msg =
                if (testSettings.realMessage)
                  TestMessage(
                    id = i,
                    name = "abc",
                    status = i % 2 == 0,
                    description = "ABC",
                    payload = payload,
                    items = Vector(TestMessage.Item(1, "A"), TestMessage.Item(2, "B")))
                else payload

              echo.tell(msg, receiver)
              i += 1
            }

            println(f"Measured send rate ${measurements.sendRate}%,.0f msg/s (target: $messageRate msg/s)")
          }

          sending.failed.foreach { ex =>
            plotProbe.ref ! Status.Failure(ex)
            system.stop(receiver)
          }

          watch(receiver)
          expectTerminated(receiver, ((totalMessages / messageRate) + 20).seconds)
          val p = plotProbe.expectMsgType[LatencyPlots]
          // only use the last repeat for the plots
          if (n == repeat) {
            plots = plots.copy(
              plot50 = plots.plot50.addAll(p.plot50),
              plot90 = plots.plot90.addAll(p.plot90),
              plot99 = plots.plot99.addAll(p.plot99))
          }
        }
      finally rep.halt()
    }

    enterBarrier("after-" + testName)
  }

  "Latency of Artery" must {
    val reporter = BenchmarkFileReporter("LatencySpec", system)

    "fail a duplicate real-message round without publishing plots" in {
      runOn(first) {
        val measurements = new LatencyMeasurements(2)
        val now = System.nanoTime()
        measurements.recordSend(0, now, now)
        measurements.recordSend(1, now, now)
        val probe = TestProbe()
        val settings = TestSettings("invalid-sequence", 100, 1, 1, realMessage = true)
        val receiver = system.actorOf(
          receiverProps(new TestRateReporter(settings.testName), settings, 2, measurements, probe.ref, reporter))
        val watcher = TestProbe()
        watcher.watch(receiver)
        val message = TestMessage(0, "abc", true, "ABC", Array[Byte](0), Vector.empty)
        receiver ! message
        receiver ! message
        probe.expectMsgType[Status.Failure].cause.getMessage should include("Expected message 1 but received 0")
        watcher.expectTerminated(receiver)
        probe.expectNoMessage(100.millis)
        measurements.roundTrip.getTotalCount should ===(1L)
      }
      enterBarrier("after-invalid-sequence")
    }

    "start echo" in {
      runOn(second) {
        // just echo back
        system.actorOf(echoProps(), "echo")
      }
      enterBarrier("echo-started")
    }

    for (s <- scenarios) {
      s"be low for ${s.testName}, at ${s.messageRate} msg/s, payloadSize = ${s.payloadSize}, realMessage = $realMessage" in
      test(s, reporter)
    }

    // TODO add more tests

  }
}
