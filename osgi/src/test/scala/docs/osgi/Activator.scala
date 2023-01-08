/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.osgi

import org.apache.pekko

case object SomeMessage

class SomeActor extends pekko.actor.Actor {
  def receive = { case SomeMessage => }
}

//#Activator
import org.apache.pekko
import pekko.actor.{ ActorSystem, Props }
import org.osgi.framework.BundleContext
import pekko.osgi.ActorSystemActivator

class Activator extends ActorSystemActivator {

  def configure(context: BundleContext, system: ActorSystem): Unit = {
    // optionally register the ActorSystem in the OSGi Service Registry
    registerService(context, system)

    val someActor = system.actorOf(Props[SomeActor](), name = "someName")
    someActor ! SomeMessage
  }

}
//#Activator
