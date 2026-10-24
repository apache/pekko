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
import java.nio.channels.SelectableChannel
import java.nio.channels.ServerSocketChannel

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.Props
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe

import com.typesafe.config.ConfigFactory

class TcpListenerAcceptErrorSpec
    extends PekkoSpec(ConfigFactory.parseString("""
      pekko.io.tcp.accept-error-retry-delay = 100ms
    """))
    with ImplicitSender {

  import SelectionHandler.ChannelAcceptable

  private class TestChannelRegistration(probe: ActorRef) extends ChannelRegistration {
    override def enableInterest(op: Int): Unit = probe ! s"enableInterest-$op"
    override def disableInterest(op: Int): Unit = probe ! s"disableInterest-$op"
    override def cancelAndClose(andThen: () => Unit): Unit = {
      probe ! "cancelAndClose"
      andThen()
    }
  }

  private class TestChannelRegistry(registration: ChannelRegistration) extends ChannelRegistry {
    @volatile var serverChannel: ServerSocketChannel = _
    override def register(channel: SelectableChannel, initialOps: Int)(implicit channelActor: ActorRef): Unit = {
      serverChannel = channel.asInstanceOf[ServerSocketChannel]
      channelActor.tell(registration, ActorRef.noSender)
    }
  }

  "TcpListener" should {

    "not re-arm OP_ACCEPT immediately after an accept error" in {
      val registrationProbe = TestProbe()
      val registry = new TestChannelRegistry(new TestChannelRegistration(registrationProbe.ref))
      val bindCommander = TestProbe()
      val handler = TestProbe()
      val selectorRouter = TestProbe()

      val listener = system.actorOf(
        Props(
          new TcpListener(
            selectorRouter.ref,
            Tcp(system),
            registry,
            bindCommander.ref,
            Tcp.Bind(handler.ref, new InetSocketAddress("127.0.0.1", 0)))))

      bindCommander.expectMsgType[Tcp.Bound]

      // make accept() fail persistently, like descriptor exhaustion does
      registry.serverChannel.close()

      listener ! ChannelAcceptable

      // the listening channel is still acceptable after the error, so re-arming
      // OP_ACCEPT immediately would spin the selector and flood the log; the
      // listener must back off instead and keep retrying on a timer
      registrationProbe.expectNoMessage(500.millis)

      listener ! Tcp.Unbind
      expectMsg(Tcp.Unbound)
    }
  }
}
