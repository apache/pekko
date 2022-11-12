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
