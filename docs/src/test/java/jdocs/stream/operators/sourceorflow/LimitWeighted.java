/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.sourceorflow;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;

import java.util.concurrent.CompletionStage;

public class LimitWeighted {
  public void simple() {
    ActorSystem<?> system = null;
    // #simple
    Source<ByteString, NotUsed> untrustedSource = Source.repeat(ByteString.fromString("element"));

    CompletionStage<ByteString> allBytes =
        untrustedSource
            .limitWeighted(
                10000, // max bytes
                bytes -> (long) bytes.length() // bytes of each chunk
                )
            .runReduce(ByteString::concat, system);
    // #simple
  }
}
