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

package org.apache.pekko.io

import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey.OP_ACCEPT

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.Props
import pekko.testkit.EventFilter
import pekko.testkit.ExplicitlyTriggeredScheduler
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActorRef
import pekko.testkit.TestKit
import pekko.testkit.TestProbe

import com.typesafe.config.ConfigFactory

class TcpListenerAcceptErrorSpec
    extends PekkoSpec("""
      pekko.io.tcp.accept-error-retry-delay = 100ms
      pekko.scheduler.implementation = "org.apache.pekko.testkit.ExplicitlyTriggeredScheduler"
    """) {

  import SelectionHandler.ChannelAcceptable

  private val scheduler = system.scheduler.asInstanceOf[ExplicitlyTriggeredScheduler]

  private class TestSetup(pullMode: Boolean) {
    val registrationProbe = TestProbe()
    val bindCommander = TestProbe()
    val handler = TestProbe()
    val selectorRouter = TestProbe()

    val registry = new ChannelRegistry {
      override def register(channel: SelectableChannel, initialOps: Int)(implicit channelActor: ActorRef): Unit = {
        val registration = new ChannelRegistration {
          override def enableInterest(op: Int): Unit = registrationProbe.ref ! op
          override def disableInterest(op: Int): Unit = registrationProbe.ref ! -op
          override def cancelAndClose(andThen: () => Unit): Unit = {
            channel.close()
            andThen()
          }
        }
        channelActor ! registration
      }
    }

    val listener = TestActorRef[TcpListener](
      Props(
        new TcpListener(
          selectorRouter.ref,
          Tcp(system),
          registry,
          bindCommander.ref,
          Tcp.Bind(handler.ref, new InetSocketAddress("127.0.0.1", 0), pullMode = pullMode))))

    bindCommander.expectMsgType[Tcp.Bound]

    def failAccept(): Unit = {
      listener.underlyingActor.channel.close()
      EventFilter[ClosedChannelException](occurrences = 1).intercept {
        listener ! ChannelAcceptable
      }
    }
  }

  private def withListener(pullMode: Boolean)(f: TestSetup => Unit): Unit = {
    val setup = new TestSetup(pullMode)
    try f(setup)
    finally setup.listener.stop()
  }

  "TcpListener" should {

    "delay selector re-arming after every accept error" in withListener(pullMode = false) { setup =>
      import setup._
      for (_ <- 1 to 2) {
        failAccept()
        registrationProbe.expectNoMessage(Duration.Zero)
        scheduler.timePasses(99.millis)
        registrationProbe.expectNoMessage(Duration.Zero)
        scheduler.timePasses(1.milli)
        registrationProbe.expectMsg(OP_ACCEPT)
      }
    }

    "keep pull-mode resume requests and duplicate notifications from bypassing the retry delay" in
    withListener(pullMode = true) { setup =>
      import setup._
      listener ! Tcp.ResumeAccepting(1)
      registrationProbe.expectMsg(OP_ACCEPT)
      failAccept()

      EventFilter[ClosedChannelException](occurrences = 0).intercept {
        listener ! Tcp.ResumeAccepting(3)
        listener ! ChannelAcceptable
        listener ! ChannelAcceptable
      }
      registrationProbe.expectNoMessage(Duration.Zero)
      scheduler.timePasses(100.millis)
      registrationProbe.expectMsg(OP_ACCEPT)
      scheduler.timePasses(100.millis)
      registrationProbe.expectNoMessage(Duration.Zero)
    }

    "re-arm immediately when accept succeeds without a pending connection" in withListener(pullMode = false) { setup =>
      import setup._
      listener ! ChannelAcceptable
      registrationProbe.expectMsg(OP_ACCEPT)
    }

    "cancel the retry when unbound" in withListener(pullMode = false) { setup =>
      import setup._
      failAccept()
      bindCommander.watch(listener)
      bindCommander.send(listener, Tcp.Unbind)
      bindCommander.expectMsg(Tcp.Unbound)
      bindCommander.expectTerminated(listener)
      scheduler.timePasses(1.second)
      registrationProbe.expectNoMessage(Duration.Zero)
    }

    "cancel the retry when stopped" in withListener(pullMode = false) { setup =>
      import setup._
      failAccept()
      bindCommander.watch(listener)
      listener.stop()
      bindCommander.expectTerminated(listener)
      scheduler.timePasses(1.second)
      registrationProbe.expectNoMessage(Duration.Zero)
    }

    for (delay <- Seq("0ms", "-1ms", "1us")) {
      s"reject an accept-error-retry-delay of $delay" in {
        val config = ConfigFactory.parseString(s"pekko.io.tcp.accept-error-retry-delay = $delay")
        val invalidSystem = ActorSystem("invalidAcceptRetryDelay", config.withFallback(PekkoSpec.testConf))
        try {
          val error = intercept[IllegalArgumentException] {
            Tcp(invalidSystem)
          }
          error.getMessage should include("accept-error-retry-delay must be at least 1 millisecond")
        } finally TestKit.shutdownActorSystem(invalidSystem)
      }
    }
  }
}
