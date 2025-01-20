/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package scala.docs.cluster

import scala.util.Success
import scala.concurrent.duration._
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.Terminated
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import com.typesafe.config.ConfigFactory
import java.util.concurrent.atomic.AtomicInteger

//#frontend
class TransformationFrontend extends Actor {

  var backends = IndexedSeq.empty[ActorRef]
  var jobCounter = 0

  def receive = {
    case job: TransformationJob if backends.isEmpty =>
      sender() ! JobFailed("Service unavailable, try again later", job)

    case job: TransformationJob =>
      jobCounter += 1
      backends(jobCounter % backends.size).forward(job)

    case BackendRegistration if !backends.contains(sender()) =>
      context.watch(sender())
      backends = backends :+ sender()

    case Terminated(a) =>
      backends = backends.filterNot(_ == a)
  }
}
//#frontend

object TransformationFrontend {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory
      .parseString(s"pekko.remote.classic.netty.tcp.port=$port")
      .withFallback(ConfigFactory.parseString("pekko.cluster.roles = [frontend]"))
      .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    val frontend = system.actorOf(Props[TransformationFrontend](), name = "frontend")

    val counter = new AtomicInteger
    import system.dispatcher
    system.scheduler.scheduleWithFixedDelay(2.seconds, 2.seconds) { () =>
      implicit val timeout: Timeout = 5.seconds
      (frontend ? TransformationJob("hello-" + counter.incrementAndGet())).foreach { result =>
        println(result)
      }
    }

  }
}
