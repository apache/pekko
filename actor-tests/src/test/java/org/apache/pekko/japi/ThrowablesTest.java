/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.japi;

import org.junit.Assert;
import org.junit.Test;

public class ThrowablesTest {
  @Test
  public void testIsNonFatal() {
    Assert.assertTrue(Throwables.isNonFatal(new IllegalArgumentException("isNonFatal")));
  }

  @Test
  public void testIsFatal() {
    Assert.assertTrue(Throwables.isFatal(new StackOverflowError("fatal")));
    Assert.assertTrue(Throwables.isFatal(new ThreadDeath()));
    Assert.assertTrue(Throwables.isFatal(new InterruptedException("fatal")));
    Assert.assertTrue(Throwables.isFatal(new LinkageError("fatal")));
  }
}
