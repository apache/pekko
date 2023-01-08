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

package jdocs.routing;

// #group
import java.util.List;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.dispatch.Dispatchers;
import org.apache.pekko.routing.Router;

import com.typesafe.config.Config;

import org.apache.pekko.routing.GroupBase;
import static jdocs.routing.CustomRouterDocTest.RedundancyRoutingLogic;

public class RedundancyGroup extends GroupBase {
  private final List<String> paths;
  private final int nbrCopies;

  public RedundancyGroup(List<String> paths, int nbrCopies) {
    this.paths = paths;
    this.nbrCopies = nbrCopies;
  }

  public RedundancyGroup(Config config) {
    this(config.getStringList("routees.paths"), config.getInt("nbr-copies"));
  }

  @Override
  public java.lang.Iterable<String> getPaths(ActorSystem system) {
    return paths;
  }

  @Override
  public Router createRouter(ActorSystem system) {
    return new Router(new RedundancyRoutingLogic(nbrCopies));
  }

  @Override
  public String routerDispatcher() {
    return Dispatchers.DefaultDispatcherId();
  }
}
// #group
