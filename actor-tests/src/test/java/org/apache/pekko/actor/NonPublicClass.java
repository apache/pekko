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

package org.apache.pekko.actor;

public class NonPublicClass {
  public static Props createProps() {
    return Props.create(MyNonPublicActorClass.class);
  }
}

class MyNonPublicActorClass extends UntypedAbstractActor {
  @Override
  public void onReceive(Object msg) {
    getSender().tell(msg, getSelf());
  }
}
