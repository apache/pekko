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

import com.typesafe.config.Config;
import java.lang.reflect.Parameter;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Extension for creating an ActorSystem before tests start and shutting it down afterwards.
 *
 * <p>This extension supports both {@code @ExtendWith} and {@code @RegisterExtension} usage
 * patterns. It implements {@link BeforeAllCallback}, {@link AfterAllCallback}, and {@link
 * ParameterResolver} to provide lifecycle management and dependency injection for ActorSystem
 * instances.
 *
 * <p><strong>Usage with @RegisterExtension (recommended for custom configuration):</strong>
 *
 * <pre>
 * class MyTest {
 *   &#64;RegisterExtension
 *   static PekkoJUnitJupiterActorSystemResource actorSystemResource =
 *     new PekkoJUnitJupiterActorSystemResource("MyTestSystem", PekkoSpec.testConf());
 *
 *   &#64;Test
 *   void myTest(ActorSystem system) {
 *     // Use the injected ActorSystem
 *     ActorRef ref = system.actorOf(Props.create(MyActor.class));
 *     // ... test logic
 *   }
 * }
 * </pre>
 *
 * <p><strong>Usage with @ExtendWith (uses default configuration):</strong>
 *
 * <pre>
 * &#64;ExtendWith(PekkoJUnitJupiterActorSystemResource.class)
 * class MyTest {
 *   &#64;Test
 *   void myTest(ActorSystem system) {
 *     // Use the injected ActorSystem with default test configuration
 *     // ... test logic
 *   }
 * }
 * </pre>
 *
 * <p>This extension supports both class-level (static) and instance-level usage:
 *
 * <ul>
 *   <li><strong>Static field</strong> ({@code @RegisterExtension static}): The ActorSystem is
 *       created before all tests and shut down after all tests complete (equivalent to JUnit 4
 *       {@code @ClassRule}).
 *   <li><strong>Instance field</strong> ({@code @RegisterExtension}): A fresh ActorSystem is
 *       created before each test and shut down after each test (equivalent to JUnit 4
 *       {@code @Rule}).
 * </ul>
 *
 * <p>Tests can receive the ActorSystem instance via parameter injection by adding an {@code
 * ActorSystem} parameter to their test method.
 */
public class PekkoJUnitJupiterActorSystemResource
    implements BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        AfterEachCallback,
        ParameterResolver {

  private ActorSystem system = null;
  private final String name;
  private final Config config;
  // Tracks whether this extension was registered as a static field (class-level lifecycle).
  // When true, beforeEach/afterEach are no-ops to preserve @ClassRule-equivalent semantics.
  // All access is guarded by synchronized methods, so volatile is unnecessary.
  private boolean classLevel = false;

  /**
   * Creates a new extension with the specified name and configuration.
   *
   * @param name the name for the ActorSystem
   * @param config the configuration for the ActorSystem, or null to use default
   */
  public PekkoJUnitJupiterActorSystemResource(String name, Config config) {
    this.name = name;
    this.config = config;
  }

  /**
   * Creates a new extension with the specified name and default test configuration.
   *
   * @param name the name for the ActorSystem
   */
  public PekkoJUnitJupiterActorSystemResource(String name) {
    this(name, PekkoSpec.testConf());
  }

  /**
   * Creates a new extension with a default name and default test configuration. This constructor is
   * used when the extension is applied via @ExtendWith.
   */
  public PekkoJUnitJupiterActorSystemResource() {
    this("PekkoJUnitJupiterTest", PekkoSpec.testConf());
  }

  // Class-level lifecycle callbacks (for static @RegisterExtension / equivalent to @ClassRule)

  @Override
  public synchronized void beforeAll(ExtensionContext context) throws Exception {
    classLevel = true;
    if (system == null) {
      system = createSystem(name, config);
    }
  }

  @Override
  public synchronized void afterAll(ExtensionContext context) throws Exception {
    if (system != null) {
      TestKit.shutdownActorSystem(system);
      system = null;
    }
  }

  // Instance-level lifecycle callbacks (for non-static @RegisterExtension / equivalent to @Rule).
  // These are no-ops when the extension is registered as a static field (classLevel == true).

  @Override
  public synchronized void beforeEach(ExtensionContext context) throws Exception {
    if (!classLevel) {
      if (system == null) {
        system = createSystem(name, config);
      }
    }
  }

  @Override
  public synchronized void afterEach(ExtensionContext context) throws Exception {
    if (!classLevel && system != null) {
      TestKit.shutdownActorSystem(system);
      system = null;
    }
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    Parameter parameter = parameterContext.getParameter();
    return parameter.getType() == ActorSystem.class;
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return getSystem();
  }

  /**
   * Get the ActorSystem instance. If the system has not been created yet, it will be created
   * lazily. This allows access to the system both from test methods and from constructors of test
   * base classes (e.g., StreamTestJupiter).
   *
   * <p>Note: It is important to not use this method from the test class constructor if the test
   * runner may create instances without actually running them, as this could lead to leaked
   * ActorSystems. Prefer using the system via parameter injection or in test methods.
   *
   * @return the ActorSystem instance
   */
  public synchronized ActorSystem getSystem() {
    if (system == null) {
      system = createSystem(name, config);
    }
    return system;
  }

  private ActorSystem createSystem(String name, Config config) {
    if (config == null) {
      return ActorSystem.create(name);
    } else {
      return ActorSystem.create(name, config);
    }
  }
}
