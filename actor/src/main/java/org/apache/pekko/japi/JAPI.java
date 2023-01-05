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
