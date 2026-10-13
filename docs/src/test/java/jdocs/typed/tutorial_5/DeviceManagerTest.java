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

package jdocs.typed.tutorial_5;

import static jdocs.typed.tutorial_5.DeviceManager.DeviceRegistered;
import static jdocs.typed.tutorial_5.DeviceManager.RequestTrackDevice;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitJUnitJupiterExtension.class)
public class DeviceManagerTest {

  @JUnitJupiterTestKit public ActorTestKit testKit = new JUnitJupiterTestKitBuilder().build();

  @Test
  public void testReplyToRegistrationRequests() {
    TestProbe<DeviceRegistered> probe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<DeviceManager.Command> managerActor = testKit.spawn(DeviceManager.create());

    managerActor.tell(new RequestTrackDevice("group1", "device", probe.getRef()));
    DeviceRegistered registered1 = probe.receiveMessage();

    // another group
    managerActor.tell(new RequestTrackDevice("group2", "device", probe.getRef()));
    DeviceRegistered registered2 = probe.receiveMessage();
    assertNotEquals(registered1.device, registered2.device);
  }
}
