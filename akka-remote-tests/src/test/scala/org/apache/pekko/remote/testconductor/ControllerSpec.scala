/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.testconductor

import java.net.InetAddress
import java.net.InetSocketAddress

import org.apache.pekko.actor.{ AddressFromURIString, PoisonPill, Props }
import org.apache.pekko.remote.testconductor.Controller.NodeInfo
import org.apache.pekko.testkit.PekkoSpec
import org.apache.pekko.testkit.ImplicitSender

object ControllerSpec {
  val config = """
    pekko.testconductor.barrier-timeout = 5s
    pekko.actor.provider = remote
    pekko.actor.debug.fsm = on
    pekko.actor.debug.lifecycle = on
    """
}

class ControllerSpec extends PekkoSpec(ControllerSpec.config) with ImplicitSender {

  val A = RoleName("a")
  val B = RoleName("b")

  "A Controller" must {

    "publish its nodes" in {
      val c = system.actorOf(Props(classOf[Controller], 1, new InetSocketAddress(InetAddress.getLocalHost, 0)))
      c ! NodeInfo(A, AddressFromURIString("akka://sys"), testActor)
      expectMsg(ToClient(Done))
      c ! NodeInfo(B, AddressFromURIString("akka://sys"), testActor)
      expectMsg(ToClient(Done))
      c ! Controller.GetNodes
      expectMsgType[Iterable[RoleName]].toSet should ===(Set(A, B))
      c ! PoisonPill // clean up so network connections don't accumulate during test run
    }

  }

}
