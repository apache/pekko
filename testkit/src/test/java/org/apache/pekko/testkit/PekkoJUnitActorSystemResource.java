/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.testkit;

import org.apache.pekko.actor.ActorSystem;

import org.apache.pekko.testkit.javadsl.TestKit;
import com.typesafe.config.Config;
import org.junit.rules.ExternalResource;

/**
 * This is a resource for creating an actor system before test start and shut it down afterwards.
 *
 * <p>To use it on a class level add this to your test class: <code>
 * &#64;ClassRule
 * public static PekkoJUnitActorSystemResource actorSystemResource =
 *   new PekkoJUnitActorSystemResource(name, config);
 *
 * private final ActorSystem system = actorSystemResource.getSystem();
 * </code> To use it on a per test level add this to your test class: <code>
 * &#64;Rule
 * public PekkoJUnitActorSystemResource actorSystemResource =
 *   new PekkoJUnitActorSystemResource(name, config);
 *
 * private ActorSystem system = null;
 *
 * &#64;Before
 * public void beforeEach() {
 *   system = actorSystemResource.getSystem();
 * }
 * </code> Note that it is important to not use <code>getSystem</code> from the constructor of the
 * test, because some test runners may create an instance of the class without actually using it
 * later, resulting in memory leaks because of not shutting down the actor system.
 */
public class PekkoJUnitActorSystemResource extends ExternalResource {
  private ActorSystem system = null;
  private final String name;
  private final Config config;

  private ActorSystem createSystem(String name, Config config) {
    try {
      if (config == null) return ActorSystem.create(name);
      else return ActorSystem.create(name, config);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public PekkoJUnitActorSystemResource(String name, Config config) {
    this.name = name;
    this.config = config;
  }

  public PekkoJUnitActorSystemResource(String name) {
    this(name, PekkoSpec.testConf());
  }

  @Override
  protected void before() throws Throwable {
    if (system == null) {
      system = createSystem(name, config);
    }
  }

  @Override
  protected void after() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  public ActorSystem getSystem() {
    if (system == null) {
      system = createSystem(name, config);
    }
    return system;
  }
}
