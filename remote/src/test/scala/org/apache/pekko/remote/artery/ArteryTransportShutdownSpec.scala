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

package org.apache.pekko.remote.artery

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise }

import org.apache.pekko
import pekko.Done
import pekko.actor.ExtendedActorSystem
import pekko.remote.{ RARP, RemoteActorRefProvider }
import pekko.stream.scaladsl.Sink
import pekko.testkit.PekkoSpec

import com.typesafe.config.ConfigFactory

class ArteryTransportShutdownSpec
    extends PekkoSpec(
      ConfigFactory
        .parseString("pekko.remote.artery.advanced.shutdown-streams-timeout = 100 ms")
        .withFallback(ArterySpecSupport.defaultConfig)) {

  "ArteryTransport shutdown" must {
    "proceed with transport shutdown when stream completion times out" in {
      assertTransportShutsDown(new StalledStreamTransport(schedulerUnavailable = false))
    }

    "proceed with transport shutdown when the system scheduler is unavailable" in {
      assertTransportShutsDown(new StalledStreamTransport(schedulerUnavailable = true))
    }
  }

  private def assertTransportShutsDown(transport: StalledStreamTransport): Unit = {
    transport.addStalledInboundStream()
    val shutdown = transport.shutdown()

    transport.transportShutdownStarted.future.futureValue should ===(Done)
    shutdown.futureValue should ===(Done)
    transport.shutdown().futureValue should ===(Done)
    transport.transportShutdownCount.get should ===(1)
  }

  private class StalledStreamTransport(schedulerUnavailable: Boolean)
      extends ArteryTransport(
        system.asInstanceOf[ExtendedActorSystem],
        RARP(system).provider.asInstanceOf[RemoteActorRefProvider]) {
    override type LifeCycle = Unit

    val transportShutdownStarted = Promise[Done]()
    val transportShutdownCount = new AtomicInteger
    private val streamCompleted = Promise[Done]()

    override protected def startTransport(): Unit = ()

    override protected def bindInboundStreams(): (Int, Int) = (0, 0)

    override protected def runInboundStreams(port: Int, bindPort: Int): Unit = ()

    override protected def scheduleShutdownStreamsTimeout(timeout: FiniteDuration)(result: => Future[Done])(
        implicit ec: ExecutionContext): Future[Done] =
      if (schedulerUnavailable) throw new IllegalStateException("scheduler unavailable")
      else super.scheduleShutdownStreamsTimeout(timeout)(result)

    override protected def shutdownTransport(): Future[Done] = {
      transportShutdownCount.incrementAndGet()
      transportShutdownStarted.trySuccess(Done)
      Future.successful(Done)
    }

    def addStalledInboundStream(): Unit =
      updateStreamMatValues(
        ArteryTransport.ControlStreamId,
        ArteryTransport.InboundStreamMatValues((), streamCompleted.future))

    override protected def outboundTransportSink(
        outboundContext: OutboundContext,
        streamId: Int,
        bufferPool: EnvelopeBufferPool): Sink[EnvelopeBuffer, Future[Done]] =
      Sink.ignore
  }
}
