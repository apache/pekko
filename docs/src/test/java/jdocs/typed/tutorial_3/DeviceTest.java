/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.typed.tutorial_3;

// #device-read-test
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

// #device-read-test
/*
//#device-read-test

public class DeviceTest {
//#device-read-test
*/
public class DeviceTest extends org.scalatestplus.junit.JUnitSuite {
  // #device-read-test

  @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Test
  public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
    TestProbe<Device.RespondTemperature> probe =
        testKit.createTestProbe(Device.RespondTemperature.class);
    ActorRef<Device.Command> deviceActor = testKit.spawn(Device.create("group", "device"));
    deviceActor.tell(new Device.ReadTemperature(42L, probe.getRef()));
    Device.RespondTemperature response = probe.receiveMessage();
    assertEquals(42L, response.requestId);
    assertEquals(Optional.empty(), response.value);
  }
  // #device-read-test

  // #device-write-read-test
  @Test
  public void testReplyWithLatestTemperatureReading() {
    TestProbe<Device.TemperatureRecorded> recordProbe =
        testKit.createTestProbe(Device.TemperatureRecorded.class);
    TestProbe<Device.RespondTemperature> readProbe =
        testKit.createTestProbe(Device.RespondTemperature.class);
    ActorRef<Device.Command> deviceActor = testKit.spawn(Device.create("group", "device"));

    deviceActor.tell(new Device.RecordTemperature(1L, 24.0, recordProbe.getRef()));
    assertEquals(1L, recordProbe.receiveMessage().requestId);

    deviceActor.tell(new Device.ReadTemperature(2L, readProbe.getRef()));
    Device.RespondTemperature response1 = readProbe.receiveMessage();
    assertEquals(2L, response1.requestId);
    assertEquals(Optional.of(24.0), response1.value);

    deviceActor.tell(new Device.RecordTemperature(3L, 55.0, recordProbe.getRef()));
    assertEquals(3L, recordProbe.receiveMessage().requestId);

    deviceActor.tell(new Device.ReadTemperature(4L, readProbe.getRef()));
    Device.RespondTemperature response2 = readProbe.receiveMessage();
    assertEquals(4L, response2.requestId);
    assertEquals(Optional.of(55.0), response2.value);
  }
  // #device-write-read-test

  // #device-read-test
}
// #device-read-test
