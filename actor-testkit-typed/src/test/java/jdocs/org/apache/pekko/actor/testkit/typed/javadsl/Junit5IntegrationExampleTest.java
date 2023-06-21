/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */


package jdocs.org.apache.pekko.actor.testkit.typed.javadsl;


import org.apache.pekko.actor.Address;
import org.apache.pekko.actor.testkit.typed.javadsl.*;
import org.apache.pekko.actor.testkit.typed.javadsl.Junit5TestKitBuilder;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// #junit5-integration
@DisplayName("Junit5")
@ExtendWith(TestKitJunit5Extension.class)
class Junit5IntegrationExampleTest {

    @Junit5TestKit
    public ActorTestKit testKit = new Junit5TestKitBuilder()
            .build();

    @Test
    void junit5Test() {
        Address address = testKit.system().address();
        assertNotNull(address);
    }

    @Test
    void testSomething() {

        ActorRef<AsyncTestingExampleTest.Echo.Ping> pinger = testKit.spawn(AsyncTestingExampleTest.Echo.create(), "ping");
        TestProbe<AsyncTestingExampleTest.Echo.Pong> probe = testKit.createTestProbe();
        pinger.tell(new AsyncTestingExampleTest.Echo.Ping("hello", probe.ref()));
        AsyncTestingExampleTest.Echo.Pong pong =  probe.expectMessage(new AsyncTestingExampleTest.Echo.Pong("hello"));
        assertEquals("hello", pong.message);
    }

    @Test
    void testSomething2() {
         ActorRef<AsyncTestingExampleTest.Echo.Ping> pinger2 = testKit.spawn(AsyncTestingExampleTest.Echo.create(), "ping2");
        TestProbe<AsyncTestingExampleTest.Echo.Pong> probe2 = testKit.createTestProbe();
        pinger2.tell(new AsyncTestingExampleTest.Echo.Ping("hello", probe2.ref()));
        AsyncTestingExampleTest.Echo.Pong pong =  probe2.expectMessage(new AsyncTestingExampleTest.Echo.Pong("hello"));
        assertEquals("hello", pong.message);
    }
}
// #junit5-integration
