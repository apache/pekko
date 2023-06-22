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

package docs.actor.io.dns

import org.apache.pekko.actor.{ ActorRef, ActorSystem }
import org.apache.pekko.io.dns.DnsProtocol
import org.apache.pekko.io.dns.DnsProtocol.Srv
import org.apache.pekko.pattern.ask
import org.apache.pekko.io.{ Dns, IO }
import org.apache.pekko.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

object DnsCompileOnlyDocSpec {

  implicit val system: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = Timeout(1.second)

  val actorRef: ActorRef = ???
  // #resolve
  val initial: Option[Dns.Resolved] = Dns(system).cache.resolve("google.com")(system, actorRef)
  val cached: Option[Dns.Resolved] = Dns(system).cache.cached("google.com")
  // #resolve

  {
    // #actor-api-inet-address
    val resolved: Future[Dns.Resolved] = (IO(Dns) ? Dns.Resolve("google.com")).mapTo[Dns.Resolved]
    // #actor-api-inet-address

  }

  {
    // #actor-api-async
    val resolved: Future[DnsProtocol.Resolved] =
      (IO(Dns) ? DnsProtocol.Resolve("google.com")).mapTo[DnsProtocol.Resolved]
    // #actor-api-async
  }

  {
    // #srv
    val resolved: Future[DnsProtocol.Resolved] =
      (IO(Dns) ? DnsProtocol.Resolve("your-service", Srv)).mapTo[DnsProtocol.Resolved]
    // #srv
  }

}
