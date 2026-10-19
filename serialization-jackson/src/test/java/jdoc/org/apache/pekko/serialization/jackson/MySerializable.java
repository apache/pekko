/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdoc.org.apache.pekko.serialization.jackson;

// #marker-interface
import org.apache.pekko.serialization.jackson.JsonSerializable;

class MyMessage implements JsonSerializable {
  public final String name;
  public final int nr;

  public MyMessage(String name, int nr) {
    this.name = name;
    this.nr = nr;
  }
}
// #marker-interface
