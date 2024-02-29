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

package org.apache.pekko.japi.pf;

import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
import scala.PartialFunction;

import static org.junit.Assert.*;

@SuppressWarnings("serial")
public class PFBuilderTest extends JUnitSuite {

  @Test
  public void pfbuilder_matchAny_should_infer_declared_input_type_for_lambda() {
    PartialFunction<String, Integer> pf =
        PFBuilder.<String, Integer>create()
            .matchEquals("hello", s -> 1)
            .matchAny(s -> Integer.valueOf(s))
            .build();

    assertTrue(pf.isDefinedAt("hello"));
    assertTrue(pf.isDefinedAt("42"));
    assertEquals(42, pf.apply("42").intValue());
  }
}
