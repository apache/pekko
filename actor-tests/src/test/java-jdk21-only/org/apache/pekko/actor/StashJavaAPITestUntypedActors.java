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

package org.apache.pekko.actor;

import static org.junit.Assert.assertEquals;

public class StashJavaAPITestUntypedActors {
    private static int testReceive(Object msg, int count, ActorRef sender, ActorRef self, UnrestrictedStash stash) {
        switch (msg) {
            case String s when count < 0 -> {
                sender.tell(s.length(), self);
                return count;
            }
            case String ignored when count == 2 -> {
                stash.unstashAll();
                return -1;
            }
            case String ignored -> {
                stash.stash();
                return count + 1;
            }
            case Integer value -> {
                assertEquals(5, value.intValue());
                return count;
            }
            default -> {
                return count;
            }
        }
    }

    public static class WithStash extends UntypedAbstractActorWithStash {
        int count = 0;

        @Override
        public void onReceive(final Object message) throws Throwable {
            count = testReceive(message, count, getSender(), getSelf(), this);
        }
    }

    public static class WithUnboundedStash extends UntypedAbstractActorWithUnboundedStash {
        int count = 0;

        @Override
        public void onReceive(final Object message) throws Throwable {
            count = testReceive(message, count, getSender(), getSelf(), this);
        }
    }

    public static class WithUnrestrictedStash extends UntypedAbstractActorWithUnrestrictedStash {
        int count = 0;

        @Override
        public void onReceive(final Object message) throws Throwable {
            count = testReceive(message, count, getSender(), getSelf(), this);
        }
    }
}
