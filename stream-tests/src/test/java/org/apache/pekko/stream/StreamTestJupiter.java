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

package org.apache.pekko.stream;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.testkit.PekkoJUnitJupiterActorSystemResource;

/**
 * base class for stream tests that provides an ActorSystem.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * public class MyStreamTest extends StreamTestJupiter {
 *   @RegisterExtension
 *   static final PekkoJUnitJupiterActorSystemResource actorSystemResource =
 *       new PekkoJUnitJupiterActorSystemResource("MyStreamTest");
 *
 *   public MyStreamTest() {
 *     super(actorSystemResource);
 *   }
 *
 *   @Test
 *   public void testStream() {
 *     // Use this.system and this.logger()
 *   }
 * }
 * }</pre>
 *
 * <p>For migration from JUnit 4 {@link StreamTest}:
 *
 * <ul>
 *   <li>Change base class from {@code StreamTest} to {@code StreamTestJupiter}
 *   <li>Replace {@code PekkoJUnitActorSystemResource} with {@code
 *       PekkoJUnitJupiterActorSystemResource}
 *   <li>Add {@code @RegisterExtension} annotation to the ActorSystem resource field
 *   <li>Remove {@code @RunWith(JUnitRunner.class)} if present
 * </ul>
 */
public abstract class StreamTestJupiter {
  protected final ActorSystem system;

  protected StreamTestJupiter(PekkoJUnitJupiterActorSystemResource actorSystemResource) {
    system = actorSystemResource.getSystem();
  }

  protected LoggingAdapter logger() {
    return system.log();
  }
}
