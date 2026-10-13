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

import java.io.Serializable;

// #messages
public interface StatsMessages {

  public record StatsJob(String text) implements Serializable {}

  public record StatsResult(double meanWordLength) implements Serializable {
    @Override
    public String toString() {
      return "meanWordLength: " + meanWordLength;
    }
  }

  public record JobFailed(String reason) implements Serializable {
    @Override
    public String toString() {
      return "JobFailed(" + reason + ")";
    }
  }
}
// #messages
