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
package aeron

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Await
import scala.concurrent.duration._

import io.aeron.Aeron
import org.agrona.IoUtil

import org.apache.pekko
import pekko.Done
import pekko.actor.ExtendedActorSystem
import pekko.actor.Props
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.STMultiNodeSpec
import pekko.stream.KillSwitches
import pekko.stream.ThrottleMode
import pekko.stream.scaladsl.Source
import pekko.testkit._
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory

object AeronStreamConsistencySpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  val barrierTimeout = 5.minutes

  commonConfig(debugConfig(on = false).withFallback(ConfigFactory.parseString(s"""
       pekko {
         loglevel = INFO
         actor {
           provider = remote
         }
         remote.artery.enabled = off
       }
       """)))
}

class AeronStreamConsistencySpecMultiJvmNode1 extends AeronStreamConsistencySpec
class AeronStreamConsistencySpecMultiJvmNode2 extends AeronStreamConsistencySpec

abstract class AeronStreamConsistencySpec
    extends AeronStreamMultiNodeSpec(AeronStreamConsistencySpec)
    with STMultiNodeSpec
    with ImplicitSender {

  import AeronStreamConsistencySpec._

  val driver = startDriver()

  val aeron = {
    val ctx = new Aeron.Context
    ctx.aeronDirectoryName(driver.aeronDirectoryName)
    Aeron.connect(ctx)
  }

  val idleCpuLevel = system.settings.config.getInt("pekko.remote.artery.advanced.aeron.idle-cpu-level")
  val taskRunner = {
    val r = new TaskRunner(system.asInstanceOf[ExtendedActorSystem], idleCpuLevel)
    r.start()
    r
  }

  val pool = new EnvelopeBufferPool(1024 * 1024, 128)

  import system.dispatcher

  override def initialParticipants = roles.size

  val streamId = 1
  val giveUpMessageAfter = 30.seconds

  override def afterAll(): Unit = {
    taskRunner.stop()
    aeron.close()
    driver.close()
    IoUtil.delete(new File(driver.aeronDirectoryName), true)
    super.afterAll()
  }

  "Message consistency of Aeron Streams" must {

    "start upd port" in {
      system.actorOf(Props[UdpPortActor](), "updPort")
      enterBarrier("udp-port-started")
    }

    "start echo" in {
      runOn(second) {
        // just echo back
        Source
          .fromGraph(new AeronSource(channel(second), streamId, aeron, taskRunner, pool, NoOpRemotingFlightRecorder, 0))
          .runWith(
            new AeronSink(
              channel(first),
              streamId,
              aeron,
              taskRunner,
              pool,
              giveUpMessageAfter,
              NoOpRemotingFlightRecorder))
      }
      enterBarrier("echo-started")
    }

    "deliver messages in order without loss" in {
      runOn(first) {
        val totalMessages = 50000
        val count = new AtomicInteger
        val done = TestLatch(1)
        val killSwitch = KillSwitches.shared("test")
        val started = TestProbe()
        val startMsg = "0".getBytes(StandardCharsets.UTF_8)
        Source
          .fromGraph(new AeronSource(channel(first), streamId, aeron, taskRunner, pool, NoOpRemotingFlightRecorder, 0))
          .via(killSwitch.flow)
          .runForeach { envelope =>
            val bytes = ByteString.fromByteBuffer(envelope.byteBuffer)
            if (bytes.length == 1 && bytes(0) == startMsg(0))
              started.ref ! Done
            else {
              val c = count.incrementAndGet()
              val x = new String(bytes.toArray, StandardCharsets.UTF_8).toInt
              if (x != c) {
                throw new IllegalArgumentException(s"# wrong message $x expected $c")
              }
              if (c == totalMessages)
                done.countDown()
            }
            pool.release(envelope)
          }
          .failed
          .foreach { _.printStackTrace }

        within(10.seconds) {
          Source(1 to 100)
            .map { _ =>
              val envelope = pool.acquire()
              envelope.byteBuffer.put(startMsg)
              envelope.byteBuffer.flip()
              envelope
            }
            .throttle(1, 200.milliseconds, 1, ThrottleMode.Shaping)
            .runWith(
              new AeronSink(
                channel(second),
                streamId,
                aeron,
                taskRunner,
                pool,
                giveUpMessageAfter,
                NoOpRemotingFlightRecorder))
          started.expectMsg(Done)
        }

        Source(1 to totalMessages)
          .throttle(10000, 1.second, 1000, ThrottleMode.Shaping)
          .map { n =>
            val envelope = pool.acquire()
            envelope.byteBuffer.put(n.toString.getBytes(StandardCharsets.UTF_8))
            envelope.byteBuffer.flip()
            envelope
          }
          .runWith(
            new AeronSink(
              channel(second),
              streamId,
              aeron,
              taskRunner,
              pool,
              giveUpMessageAfter,
              NoOpRemotingFlightRecorder))

        Await.ready(done, 20.seconds)
        killSwitch.shutdown()
      }
      enterBarrier("after-1")
    }

  }
}
