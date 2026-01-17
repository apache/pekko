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

import org.apache.pekko
import pekko.actor.{ ActorRef, ActorSystem }
import pekko.io.dns.DnsProtocol
import pekko.io.dns.DnsProtocol.Srv
import pekko.pattern.ask
import pekko.io.{ Dns, IO }
import pekko.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

object DnsCompileOnlyDocSpec {

  implicit val system: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = Timeout(1.second)

  val actorRef: ActorRef = ???

  {
    // #resolve
    val resolve = DnsProtocol.Resolve("google.com", DnsProtocol.ipRequestType())
    val initial: Option[DnsProtocol.Resolved] = Dns.resolve(resolve, system, actorRef)
    val cached: Option[DnsProtocol.Resolved] = Dns.cached(resolve)(system)
    // #resolve
  }

  {
    // #actor-api-inet-address
    val resolved
        : Future[DnsProtocol.Resolved] = (IO(Dns) ? DnsProtocol.Resolve("google.com")).mapTo[DnsProtocol.Resolved]
    // #actor-api-inet-address
  }

  {
    // #actor-api-async
    val resolved
        : Future[DnsProtocol.Resolved] = (IO(Dns) ? DnsProtocol.Resolve("google.com")).mapTo[DnsProtocol.Resolved]
    // #actor-api-async
  }

  {
    // #srv
    val resolved: Future[DnsProtocol.Resolved] = (IO(Dns) ? DnsProtocol.Resolve("your-service", Srv)).mapTo[
      DnsProtocol.Resolved]
    // #srv
  }

}
