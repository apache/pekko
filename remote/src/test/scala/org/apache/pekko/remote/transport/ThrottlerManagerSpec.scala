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

package org.apache.pekko.remote.transport

import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Address
import pekko.actor.Props
import pekko.event.LoggingAdapter
import pekko.remote.transport.ActorTransportAdapter.{ AssociateUnderlying, ListenerRegistered }
import pekko.remote.transport.AssociationHandle.{ HandleEvent, HandleEventListener, InboundPayload }
import pekko.remote.transport.ThrottlerTransportAdapter.{ ForceDisassociate, ForceDisassociateAck }
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory

@nowarn("msg=deprecated")
class ThrottlerManagerSpec
    extends PekkoSpec(ConfigFactory.parseString("""
    pekko.actor.provider = remote
    pekko.remote.artery.enabled = off
    pekko.remote.classic.netty.tcp.port = 0
  """))
    with ImplicitSender {

  private val addrLocal = Address("pekko", "systemA", "hostA", 1001)
  private val addrRemote = Address("pekko", "systemB", "hostB", 1002)

  private class TestAssociationHandle extends AssociationHandle {
    val disassociations = new AtomicInteger(0)
    override val localAddress: Address = addrLocal
    override val remoteAddress: Address = addrRemote
    override val readHandlerPromise: Promise[HandleEventListener] = Promise()
    override def write(payload: ByteString): Boolean = throw new RuntimeException("write failed")
    override def disassociate(reason: String, log: LoggingAdapter): Unit = {
      disassociations.incrementAndGet()
      ()
    }
  }

  private class MockTransport(handle: AssociationHandle) extends Transport {
    override def schemeIdentifier: String = "mock"
    override def isResponsibleFor(address: Address): Boolean = true
    override def maximumPayloadBytes: Int = 128000
    override def listen: Future[(Address, Promise[Transport.AssociationEventListener])] =
      Future.failed(new UnsupportedOperationException("not needed by this test"))
    override def associate(remoteAddress: Address): Future[AssociationHandle] = Future.successful(handle)
    override def shutdown(): Future[Boolean] = Future.successful(true)
  }

  "ThrottlerManager" should {

    "stop a failed ThrottledAssociation and prune it from the handle table" in {
      val handle = new TestAssociationHandle
      val manager = system.actorOf(Props(new ThrottlerManager(new MockTransport(handle))), "throttlerManager")

      manager ! ListenerRegistered(new Transport.AssociationEventListener {
        override def notify(ev: Transport.AssociationEvent): Unit = ()
      })

      val statusPromise = Promise[AssociationHandle]()
      manager ! AssociateUnderlying(addrRemote, statusPromise)
      val wrappedHandle = Await.result(statusPromise.future, 3.seconds).asInstanceOf[ThrottlerHandle]
      val child = wrappedHandle.throttlerActor
      watch(child)

      // the upstream listener throws when the FSM forwards the payload to it,
      // failing the ThrottledAssociation
      wrappedHandle.readHandlerPromise.success(new HandleEventListener {
        override def notify(ev: HandleEvent): Unit = throw new RuntimeException("listener failed")
      })
      child ! InboundPayload(ByteString("boom"))

      // the failed association must be stopped rather than restarted: a restarted
      // instance re-enters its initial state but Handle is never re-sent, so it
      // would silently drop payloads forever
      expectTerminated(child, max = 3.seconds)

      // postStop disassociated the original handle exactly once
      awaitAssert(handle.disassociations.get() should ===(1))

      // and the terminated handle must not be iterated by later commands
      manager ! ForceDisassociate(addrRemote)
      expectMsg(ForceDisassociateAck)
      handle.disassociations.get() should ===(1)
    }
  }
}
