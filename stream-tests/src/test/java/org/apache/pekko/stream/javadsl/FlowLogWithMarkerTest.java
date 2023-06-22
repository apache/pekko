/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.event.LogMarker;
import org.apache.pekko.event.MarkerLoggingAdapter;
import org.apache.pekko.japi.function.Function;

public class FlowLogWithMarkerTest {

  public static // #signature
  <In> Flow<In, In, NotUsed> logWithMarker(
      String name,
      Function<In, LogMarker> marker,
      Function<In, Object> extract,
      MarkerLoggingAdapter log)
        // #signature
      {
    return Flow.<In>create().logWithMarker(name, marker, extract, log);
  }
}
