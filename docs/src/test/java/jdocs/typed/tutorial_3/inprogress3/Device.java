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

package jdocs.typed.tutorial_3.inprogress3;

public class Device {

  public interface Command {}

  // #write-protocol-1
  public static final class RecordTemperature implements Command {
    final double value;

    public RecordTemperature(double value) {
      this.value = value;
    }
  }
  // #write-protocol-1
}
