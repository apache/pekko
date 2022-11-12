/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster.singleton;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.SupervisorStrategy;

// #singleton-supervisor-actor-usage-imports
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.singleton.ClusterSingletonManager;
import org.apache.pekko.cluster.singleton.ClusterSingletonManagerSettings;
// #singleton-supervisor-actor-usage-imports

abstract class ClusterSingletonSupervision extends AbstractActor {

  public ActorRef createSingleton(String name, Props props, SupervisorStrategy supervisorStrategy) {
    // #singleton-supervisor-actor-usage
    return getContext()
        .system()
        .actorOf(
            ClusterSingletonManager.props(
                Props.create(
                    SupervisorActor.class, () -> new SupervisorActor(props, supervisorStrategy)),
                PoisonPill.getInstance(),
                ClusterSingletonManagerSettings.create(getContext().system())),
            name = name);
    // #singleton-supervisor-actor-usage
  }
}
