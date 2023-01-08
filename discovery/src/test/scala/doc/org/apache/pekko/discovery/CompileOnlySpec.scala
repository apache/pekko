/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package doc.org.apache.pekko.discovery

import org.apache.pekko.actor.ActorSystem

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object CompileOnlySpec {

  // #loading
  import org.apache.pekko.discovery.Discovery

  val system = ActorSystem()
  val serviceDiscovery = Discovery(system).discovery
  // #loading

  // #basic
  import org.apache.pekko.discovery.Lookup

  serviceDiscovery.lookup(Lookup("pekko.io"), 1.second)
  // Convenience for a Lookup with only a serviceName
  serviceDiscovery.lookup("pekko.io", 1.second)
  // #basic

  // #full
  import org.apache.pekko
  import pekko.discovery.Lookup
  import pekko.discovery.ServiceDiscovery.Resolved

  val lookup: Future[Resolved] =
    serviceDiscovery.lookup(Lookup("pekko.io").withPortName("remoting").withProtocol("tcp"), 1.second)
  // #full

  // compiler
  lookup.foreach(println)

}
