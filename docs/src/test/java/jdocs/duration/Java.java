/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2013-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.duration;

// #import
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.Deadline;

import static org.junit.Assert.assertTrue;
// #import

class Java {
  public void demo() {
    // #dsl
    final Duration fivesec = Duration.create(5, "seconds");
    final Duration threemillis = Duration.create("3 millis");
    final Duration diff = fivesec.minus(threemillis);
    assertTrue(diff.lt(fivesec));
    assertTrue(Duration.Zero().lt(Duration.Inf()));
    // #dsl
    // #deadline
    final Deadline deadline = Duration.create(10, "seconds").fromNow();
    final Duration rest = deadline.timeLeft();
    // #deadline
    rest.toString();
  }
}
