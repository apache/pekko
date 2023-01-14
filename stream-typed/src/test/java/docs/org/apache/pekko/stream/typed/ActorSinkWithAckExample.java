/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.stream.typed;

// #actor-sink-ref-with-backpressure
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.typed.javadsl.ActorSink;
// #actor-sink-ref-with-backpressure

public class ActorSinkWithAckExample {

  // #actor-sink-ref-with-backpressure

  enum Ack {
    INSTANCE;
  }

  interface Protocol {}

  class Init implements Protocol {
    private final ActorRef<Ack> ack;

    public Init(ActorRef<Ack> ack) {
      this.ack = ack;
    }
  }

  class Message implements Protocol {
    private final ActorRef<Ack> ackTo;
    private final String msg;

    public Message(ActorRef<Ack> ackTo, String msg) {
      this.ackTo = ackTo;
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
  // #actor-sink-ref-with-backpressure

  final ActorSystem<Void> system = null;

  {
    // #actor-sink-ref-with-backpressure

    final ActorRef<Protocol> actorRef = // spawned actor
        null; // #hidden

    final Complete completeMessage = new Complete();

    final Sink<String, NotUsed> sink =
        ActorSink.actorRefWithBackpressure(
            actorRef,
            (responseActorRef, element) -> new Message(responseActorRef, element),
            (responseActorRef) -> new Init(responseActorRef),
            Ack.INSTANCE,
            completeMessage,
            (exception) -> new Fail(exception));

    Source.single("msg1").runWith(sink, system);
    // #actor-sink-ref-with-backpressure
  }
}
