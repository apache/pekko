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

package org.apache.pekko.actor.typed;

import org.apache.pekko.actor.setup.ActorSystemSetup;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.function.Function;

import static junit.framework.TestCase.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExtensionsTest extends JUnitSuite {

  public static class MyExtImpl implements Extension {}

  public static class MyExtension extends ExtensionId<MyExtImpl> {

    private static final MyExtension instance = new MyExtension();

    private MyExtension() {}

    public static MyExtension getInstance() {
      return instance;
    }

    public MyExtImpl createExtension(ActorSystem<?> system) {
      return new MyExtImpl();
    }

    public static MyExtImpl get(ActorSystem<?> system) {
      return instance.apply(system);
    }
  }

  public static class MyExtImplViaSetup extends MyExtImpl {}

  public static class MyExtensionSetup extends ExtensionSetup<MyExtImpl> {
    public MyExtensionSetup(Function<ActorSystem<?>, MyExtImpl> createExtension) {
      super(MyExtension.getInstance(), createExtension);
    }
  }

  @Test
  public void loadJavaExtensionsFromConfig() {
    Config cfg =
        ConfigFactory.parseString(
                "pekko.actor.typed.extensions += \"org.apache.pekko.actor.typed.ExtensionsTest$MyExtension\"")
            .resolve();
    final ActorSystem<Object> system =
        ActorSystem.create(Behaviors.empty(), "loadJavaExtensionsFromConfig", cfg);

    try {
      // note that this is not the intended end user way to access it
      assertTrue(system.hasExtension(MyExtension.getInstance()));

      MyExtImpl instance1 = MyExtension.get(system);
      MyExtImpl instance2 = MyExtension.get(system);

      assertSame(instance1, instance2);
    } finally {
      system.terminate();
    }
  }

  @Test
  public void loadScalaExtension() {
    final ActorSystem<Object> system = ActorSystem.create(Behaviors.empty(), "loadScalaExtension");
    try {
      DummyExtension1 instance1 = DummyExtension1.get(system);
      DummyExtension1 instance2 = DummyExtension1.get(system);

      assertSame(instance1, instance2);
    } finally {
      system.terminate();
    }
  }

  @Test
  public void overrideExtensionsViaActorSystemSetup() {
    final ActorSystem<Object> system =
        ActorSystem.create(
            Behaviors.empty(),
            "overrideExtensionsViaActorSystemSetup",
            ActorSystemSetup.create(new MyExtensionSetup(sys -> new MyExtImplViaSetup())));

    try {
      MyExtImpl instance1 = MyExtension.get(system);
      assertEquals(MyExtImplViaSetup.class, instance1.getClass());

      MyExtImpl instance2 = MyExtension.get(system);
      assertSame(instance1, instance2);

    } finally {
      system.terminate();
    }
  }
}
