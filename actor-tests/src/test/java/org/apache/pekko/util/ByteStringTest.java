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

package org.apache.pekko.util;

import static junit.framework.TestCase.assertEquals;

import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class ByteStringTest extends JUnitSuite {

  @Test
  public void testCreation() {
    final ByteString s1 = ByteString.fromString("");
    final ByteString s2 = ByteString.fromInts(1, 2, 3);
  }

  @Test
  public void testBuilderCreation() {
    final ByteStringBuilder sb = ByteString.createBuilder();
    sb.append(ByteString.fromString("Hello"));
    sb.append(ByteString.fromString(" "));
    sb.append(ByteString.fromString("World"));
    assertEquals(ByteString.fromString("Hello World"), sb.result());
  }
}
