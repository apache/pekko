/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor;

import static org.junit.Assert.assertEquals;

import java.util.Optional;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class AddressTest extends JUnitSuite {

  @Test
  public void portAddressAccessible() {
    Address address = new Address("pekko", "MySystem", "localhost", 2525);
    assertEquals(Optional.of(2525), address.getPort());
    assertEquals(Optional.of("localhost"), address.getHost());
  }
}
