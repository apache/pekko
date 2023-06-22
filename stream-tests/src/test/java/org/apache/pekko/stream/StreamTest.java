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
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.scalatestplus.junit.JUnitSuite;

public abstract class StreamTest extends JUnitSuite {
  protected final ActorSystem system;

  protected StreamTest(PekkoJUnitActorSystemResource actorSystemResource) {
    system = actorSystemResource.getSystem();
  }
}
