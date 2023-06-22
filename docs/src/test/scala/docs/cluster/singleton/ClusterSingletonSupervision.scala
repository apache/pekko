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

package docs.cluster.singleton

//#singleton-supervisor-actor
import org.apache.pekko.actor.{ Actor, Props, SupervisorStrategy }
class SupervisorActor(childProps: Props, override val supervisorStrategy: SupervisorStrategy) extends Actor {
  val child = context.actorOf(childProps, "supervised-child")

  def receive = {
    case msg => child.forward(msg)
  }
}
//#singleton-supervisor-actor

import org.apache.pekko.actor.Actor
abstract class ClusterSingletonSupervision extends Actor {
  import org.apache.pekko.actor.{ ActorRef, Props, SupervisorStrategy }
  def createSingleton(name: String, props: Props, supervisorStrategy: SupervisorStrategy): ActorRef = {
    // #singleton-supervisor-actor-usage
    import org.apache.pekko
    import pekko.actor.{ PoisonPill, Props }
    import pekko.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings }
    context.system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(classOf[SupervisorActor], props, supervisorStrategy),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(context.system)),
      name = name)
    // #singleton-supervisor-actor-usage
  }
}
