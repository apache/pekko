/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.testkit.DeadLettersFilter
import pekko.testkit.PekkoSpec
import pekko.testkit.TestEvent.Mute
import pekko.testkit.WithLogCapturing

object ConcurrentStartupShardingSpec {

  val config =
    """
    pekko.actor.provider = "cluster"
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.log-dead-letters = off
    pekko.log-dead-letters-during-shutdown = off
    pekko.cluster.sharding.verbose-debug-logging = on
    pekko.actor {
      default-dispatcher {
        executor = "fork-join-executor"
        fork-join-executor {
          parallelism-min = 5
          parallelism-max = 5
        }
      }
    }
    """

  object Starter {
    def props(n: Int, probe: ActorRef): Props =
      Props(new Starter(n, probe))
  }

  class Starter(n: Int, probe: ActorRef) extends Actor {

    override def preStart(): Unit = {
      val region =
        ClusterSharding(context.system).start(s"type-$n", Props.empty, ClusterShardingSettings(context.system),
          {
            case msg => (msg.toString, msg)
          }, _ => "1")
      probe ! region
    }

    def receive = {
      case _ =>
    }
  }
}

class ConcurrentStartupShardingSpec extends PekkoSpec(ConcurrentStartupShardingSpec.config) with WithLogCapturing {
  import ConcurrentStartupShardingSpec._

  // mute logging of deadLetters
  if (!log.isDebugEnabled)
    system.eventStream.publish(Mute(DeadLettersFilter[Any]))

  // The intended usage is to start sharding in one (or a few) places when the the ActorSystem
  // is started and not to do it concurrently from many threads. However, we can do our best and when using
  // FJP the Await will create additional threads when needed.
  "Concurrent Sharding startup" must {
    "init cluster" in {
      Cluster(system).join(Cluster(system).selfAddress)
      awaitAssert(Cluster(system).selfMember.status should ===(MemberStatus.Up))

      val total = 20
      (1 to total).foreach { n =>
        system.actorOf(Starter.props(n, testActor))
      }

      receiveN(total, 60.seconds)
    }
  }

}
