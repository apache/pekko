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

// #sample
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.stream.CompletionStrategy;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.typed.javadsl.ActorSource;

import java.util.Optional;

class StreamFeeder extends AbstractBehavior<StreamFeeder.Emitted> {
  /** Signals that the latest element is emitted into the stream */
  public enum Emitted {
    INSTANCE;
  }

  public interface Event {}

  public static class Element implements Event {
    public final String content;

    public Element(String content) {
      this.content = content;
    }

    @Override
    public String toString() {
      return "Element(" + content + ")";
    }
  }

  public enum ReachedEnd implements Event {
    INSTANCE;
  }

  public static class FailureOccured implements Event {
    public final Exception ex;

    public FailureOccured(Exception ex) {
      this.ex = ex;
    }
  }

  public static Behavior<Emitted> create() {
    return Behaviors.setup(StreamFeeder::new);
  }

  private int counter = 0;
  private final ActorRef<Event> streamSource;

  private StreamFeeder(ActorContext<Emitted> context) {
    super(context);
    streamSource = runStream(context.getSelf(), context.getSystem());
    streamSource.tell(new Element("first"));
  }

  @Override
  public Receive<Emitted> createReceive() {
    return newReceiveBuilder().onMessage(Emitted.class, this::onEmitted).build();
  }

  private static ActorRef<Event> runStream(ActorRef<Emitted> ackReceiver, ActorSystem<?> system) {
    Source<Event, ActorRef<Event>> source =
        ActorSource.actorRefWithBackpressure(
            ackReceiver,
            Emitted.INSTANCE,
            // complete when we send ReachedEnd
            (msg) -> {
              if (msg == ReachedEnd.INSTANCE) return Optional.of(CompletionStrategy.draining());
              else return Optional.empty();
            },
            (msg) -> {
              if (msg instanceof FailureOccured) return Optional.of(((FailureOccured) msg).ex);
              else return Optional.empty();
            });

    return source.to(Sink.foreach(System.out::println)).run(system);
  }

  private Behavior<Emitted> onEmitted(Emitted message) {
    if (counter < 5) {
      streamSource.tell(new Element(String.valueOf(counter)));
      counter++;
      return this;
    } else {
      streamSource.tell(ReachedEnd.INSTANCE);
      return Behaviors.stopped();
    }
  }
}
// #sample

public class ActorSourceWithBackpressureExample {

  public static void main(String[] args) {
    // #sample
    ActorSystem<StreamFeeder.Emitted> system =
        ActorSystem.create(StreamFeeder.create(), "stream-feeder");

    // will print:
    // Element(first)
    // Element(0)
    // Element(1)
    // Element(2)
    // Element(3)
    // Element(4)
    // #sample
  }
}
