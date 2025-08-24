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

package org.apache.pekko.stream.typed.javadsl;

import java.util.Optional;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

public class ActorSourceSinkCompileTest {

  interface Protocol {}

  class Init implements Protocol {}

  class Msg implements Protocol {}

  class Complete implements Protocol {}

  class Failure implements Protocol {
    public Exception ex;
  }

  {
    final ActorSystem<String> system = null;
  }

  {
    final ActorRef<String> ref = null;

    Source.<String>queue(10)
        .map(s -> s + "!")
        .to(ActorSink.actorRef(ref, "DONE", ex -> "FAILED: " + ex.getMessage()));
  }

  {
    final ActorRef<Protocol> ref = null;

    Source.<String>queue(10)
        .to(
            ActorSink.actorRefWithBackpressure(
                ref,
                (sender, msg) -> new Init(),
                (sender) -> new Msg(),
                "ACK",
                new Complete(),
                (f) -> new Failure()));
  }

  {
    ActorSource.actorRef(
            (m) -> m == "complete", (m) -> Optional.empty(), 10, OverflowStrategy.dropBuffer())
        .to(Sink.seq());
  }

  {
    ActorSource.actorRef(
            (m) -> false,
            (m) -> (m instanceof Failure) ? Optional.of(((Failure) m).ex) : Optional.empty(),
            10,
            OverflowStrategy.dropBuffer())
        .to(Sink.seq());
  }
}
