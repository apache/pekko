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

// #actor-source-ref
import java.util.Optional;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.japi.JavaPartialFunction;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.typed.javadsl.ActorSource;

// #actor-source-ref

public class ActorSourceExample {

  // #actor-source-ref

  interface Protocol {}

  class Message implements Protocol {
    private final String msg;

    public Message(String msg) {
      this.msg = msg;
    }
  }

  class Complete implements Protocol {}

  class Fail implements Protocol {
    private final Exception ex;

    public Fail(Exception ex) {
      this.ex = ex;
    }
  }
  // #actor-source-ref

  {
    final ActorSystem<Void> system = null;
    // #actor-source-ref

    final Source<Protocol, ActorRef<Protocol>> source =
        ActorSource.actorRef(
            (m) -> m instanceof Complete,
            (m) -> (m instanceof Fail) ? Optional.of(((Fail) m).ex) : Optional.empty(),
            8,
            OverflowStrategy.fail());

    final ActorRef<Protocol> ref =
        source
            .collect(
                new JavaPartialFunction<Protocol, String>() {
                  public String apply(Protocol p, boolean isCheck) {
                    if (p instanceof Message) {
                      return ((Message) p).msg;
                    } else {
                      throw noMatch();
                    }
                  }
                })
            .to(Sink.foreach(System.out::println))
            .run(system);

    ref.tell(new Message("msg1"));
    // ref.tell("msg2"); Does not compile
    // #actor-source-ref
  }
}
