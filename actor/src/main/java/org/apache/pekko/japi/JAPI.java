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

package org.apache.pekko.japi;

import scala.collection.Seq;

public class JAPI {

  @SafeVarargs
  public static <T> Seq<T> seq(T... ts) {
    return Util.immutableSeq(ts);
  }
}
