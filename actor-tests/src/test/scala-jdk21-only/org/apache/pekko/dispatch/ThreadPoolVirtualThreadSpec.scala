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

import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.testkit.{ ImplicitSender, PekkoSpec }

import com.typesafe.config.ConfigFactory

object ThreadPoolVirtualThreadSpec {
  val config = ConfigFactory.parseString("""
      |custom {
      |  task-dispatcher {
      |    mailbox-type = "org.apache.pekko.dispatch.SingleConsumerOnlyUnboundedMailbox"
      |    throughput = 5
      |    executor = "thread-pool-executor"
      |    thread-pool-executor {
      |      fixed-pool-size = 4
      |      virtualize = on
      |    }
      |  }
      |}
    """.stripMargin)

  class ThreadNameActor extends Actor {

    override def receive = {
      case "ping" =>
        sender() ! Thread.currentThread().getName
    }
  }

}

class ThreadPoolVirtualThreadSpec extends PekkoSpec(ThreadPoolVirtualThreadSpec.config) with ImplicitSender {
  import ThreadPoolVirtualThreadSpec._

  "ThreadPool" must {

    "support virtualization with Virtual Thread" in {
      val actor = system.actorOf(Props(new ThreadNameActor).withDispatcher("custom.task-dispatcher"))
      for (_ <- 1 to 1000) {
        actor ! "ping"
        expectMsgPF() { case name: String =>
          name should include("ThreadPoolVirtualThreadSpec-custom.task-dispatcher-virtual-thread-")
        }
      }
    }

  }
}
