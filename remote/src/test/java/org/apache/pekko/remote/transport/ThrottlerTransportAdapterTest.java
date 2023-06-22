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

package org.apache.pekko.remote.transport;

// compile only; verify java interop
public class ThrottlerTransportAdapterTest {

  public void compileThrottlerTransportAdapterDirections() {
    acceptDirection(ThrottlerTransportAdapter.bothDirection());
    acceptDirection(ThrottlerTransportAdapter.receiveDirection());
    acceptDirection(ThrottlerTransportAdapter.sendDirection());
  }

  public void compleThrottleMode() {
    acceptThrottleMode(ThrottlerTransportAdapter.unthrottledThrottleMode());
    acceptThrottleMode(ThrottlerTransportAdapter.blackholeThrottleMode());
    acceptThrottleMode(new ThrottlerTransportAdapter.TokenBucket(0, 0.0, 0, 0));
  }

  void acceptDirection(ThrottlerTransportAdapter.Direction dir) {}

  void acceptThrottleMode(ThrottlerTransportAdapter.ThrottleMode mode) {}
}
