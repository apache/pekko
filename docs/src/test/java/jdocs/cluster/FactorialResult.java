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

package jdocs.cluster;

import java.math.BigInteger;
import java.io.Serializable;

public class FactorialResult implements Serializable {
  private static final long serialVersionUID = 1L;
  public final int n;
  public final BigInteger factorial;

  FactorialResult(int n, BigInteger factorial) {
    this.n = n;
    this.factorial = factorial;
  }
}
