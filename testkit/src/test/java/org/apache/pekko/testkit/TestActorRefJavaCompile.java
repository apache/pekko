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

import org.apache.pekko.actor.Actor;
import org.apache.pekko.actor.Props;

public class TestActorRefJavaCompile {

  public void shouldBeAbleToCompileWhenUsingApply() {
    // Just dummy calls to make sure it compiles
    TestActorRef<Actor> ref = TestActorRef.create(null, Props.empty());
    ref.toString();
    TestActorRef<Actor> namedRef = TestActorRef.create(null, Props.empty(), "namedActor");
    namedRef.toString();
    TestActorRef<Actor> supervisedRef = TestActorRef.create(null, Props.empty(), ref);
    supervisedRef.toString();
    TestActorRef<Actor> namedSupervisedRef =
        TestActorRef.create(null, Props.empty(), ref, "namedActor");
    namedSupervisedRef.toString();
  }
}
