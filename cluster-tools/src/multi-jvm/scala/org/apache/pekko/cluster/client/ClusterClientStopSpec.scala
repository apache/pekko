/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.client

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.annotation.nowarn
import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.cluster.Cluster
import pekko.cluster.pubsub.{ DistributedPubSub, DistributedPubSubMediator }
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.{ MultiNodeConfig, MultiNodeSpec, STMultiNodeSpec }
import pekko.testkit.{ EventFilter, ImplicitSender }

object ClusterClientStopSpec extends MultiNodeConfig {
  val client = role("client")
  val first = role("first")
  val second = role("second")
  commonConfig(ConfigFactory.parseString("""
    pekko.loglevel = INFO
    pekko.actor.provider = "cluster"
    pekko.remote.log-remote-lifecycle-events = off
    pekko.cluster.client {
      heartbeat-interval = 1s
      acceptable-heartbeat-pause = 1s
      reconnect-timeout = 3s
      receptionist.number-of-contacts = 1

    }
    pekko.test.filter-leeway = 10s
  """))

  class Service extends Actor {
    def receive = {
      case msg => sender() ! msg
    }
  }
}

class ClusterClientStopMultiJvmNode1 extends ClusterClientStopSpec
class ClusterClientStopMultiJvmNode2 extends ClusterClientStopSpec
class ClusterClientStopMultiJvmNode3 extends ClusterClientStopSpec

@nowarn("msg=deprecated")
class ClusterClientStopSpec extends MultiNodeSpec(ClusterClientStopSpec) with STMultiNodeSpec with ImplicitSender {

  import ClusterClientStopSpec._

  override def initialParticipants: Int = 3

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system).join(node(to).address)
      ClusterClientReceptionist(system)
    }
    enterBarrier(from.name + "-joined")
  }

  def awaitCount(expected: Int): Unit = {
    awaitAssert {
      DistributedPubSub(system).mediator ! DistributedPubSubMediator.Count
      val actual = expectMsgType[Int]
      actual should ===(expected)
    }
  }

  def initialContacts = Set(first, second).map { r =>
    node(r) / "system" / "receptionist"
  }

  "A Cluster Client" should {

    "startup cluster" in within(30.seconds) {
      join(first, first)
      join(second, first)
      runOn(first) {
        val service = system.actorOf(Props(classOf[Service]), "testService")
        ClusterClientReceptionist(system).registerService(service)
      }
      runOn(first, second) {
        awaitCount(1)
      }

      enterBarrier("cluster-started")
    }

    "stop if re-establish fails for too long time" in within(20.seconds) {
      runOn(client) {
        val c = system.actorOf(
          ClusterClient.props(ClusterClientSettings(system).withInitialContacts(initialContacts)),
          "client1")
        c ! ClusterClient.Send("/user/testService", "hello", localAffinity = true)
        expectMsgType[String](3.seconds) should be("hello")
        enterBarrier("was-in-contact")

        watch(c)

        expectTerminated(c, 10.seconds)
        EventFilter.warning(start = "Receptionist reconnect not successful within", occurrences = 1)

      }

      runOn(first, second) {
        enterBarrier("was-in-contact")
        Await.ready(system.terminate(), 10.seconds)

      }

    }

  }

}
