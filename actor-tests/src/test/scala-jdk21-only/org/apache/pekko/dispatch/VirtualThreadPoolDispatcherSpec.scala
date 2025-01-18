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

package org.apache.pekko.dispatch

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.testkit.{ ImplicitSender, PekkoSpec }

object VirtualThreadPoolDispatcherSpec {
  val config = ConfigFactory.parseString("""
      |virtual-thread-dispatcher {
      |  executor = virtual-thread-executor
      |}
    """.stripMargin)

  class InnocentActor extends Actor {

    override def receive = {
      case "ping" =>
        sender() ! Thread.currentThread().getName
    }
  }

}

class VirtualThreadPoolDispatcherSpec extends PekkoSpec(VirtualThreadPoolDispatcherSpec.config) with ImplicitSender {
  import VirtualThreadPoolDispatcherSpec._

  "VirtualThreadPool support" must {

    "handle simple dispatch" in {
      val innocentActor = system.actorOf(Props(new InnocentActor).withDispatcher("virtual-thread-dispatcher"))
      for (_ <- 1 to 1000) {
        innocentActor ! "ping"
        expectMsgPF() { case name: String =>
          name should include("VirtualThreadPoolDispatcherSpec-virtual-thread-dispatcher-virtual-thread-")
        }
      }
    }

  }
}
