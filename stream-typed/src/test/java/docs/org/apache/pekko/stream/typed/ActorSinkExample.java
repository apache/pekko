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

package docs.org.apache.pekko.stream.typed;

// #actor-sink-ref
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.typed.javadsl.ActorSink;
// #actor-sink-ref

public class ActorSinkExample {

  // #actor-sink-ref

  interface Protocol {}

  class Message implements Protocol {
    private final String msg;

    public Message(String msg) {
      this.msg = msg;
    }
  }

  class Complete implements Protocol {}

  class Fail implements Protocol {
    private final Throwable ex;

    public Fail(Throwable ex) {
      this.ex = ex;
    }
  }
  // #actor-sink-ref

  final ActorSystem<Void> system = null;

  {
    // #actor-sink-ref

    final ActorRef<Protocol> actor = null;

    final Sink<Protocol, NotUsed> sink = ActorSink.actorRef(actor, new Complete(), Fail::new);

    Source.<Protocol>single(new Message("msg1")).runWith(sink, system);
    // #actor-sink-ref
  }
}
