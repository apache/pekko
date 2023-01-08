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

package org.apache.pekko.util;

import java.io.Serializable;

/*
 * IMPORTANT: do not change this file, the line numbers are verified in LineNumberSpec
 */

public class LineNumberSpecCodeForJava {

  public static interface F extends Serializable {
    public String doit(String arg);
  }

  public F f1() {
    return (s) -> s;
  }

  public F f2() {
    return (s) -> {
      System.out.println(s);
      return s;
    };
  }

  public F f3() {
    return new F() {
      private static final long serialVersionUID = 1L;

      @Override
      public String doit(String arg) {
        return arg;
      }
    };
  }
}
