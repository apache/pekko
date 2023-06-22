/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.persistence;

// #persistent-actor-example

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.persistence.AbstractPersistentActor;
import org.apache.pekko.persistence.SnapshotOffer;

import java.io.Serializable;
import java.util.ArrayList;

class Cmd implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String data;

  public Cmd(String data) {
    this.data = data;
  }

  public String getData() {
    return data;
  }
}

class Evt implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String data;

  public Evt(String data) {
    this.data = data;
  }

  public String getData() {
    return data;
  }
}

class ExampleState implements Serializable {
  private static final long serialVersionUID = 1L;
  private final ArrayList<String> events;

  public ExampleState() {
    this(new ArrayList<>());
  }

  public ExampleState(ArrayList<String> events) {
    this.events = events;
  }

  public ExampleState copy() {
    return new ExampleState(new ArrayList<>(events));
  }

  public void update(Evt evt) {
    events.add(evt.getData());
  }

  public int size() {
    return events.size();
  }

  @Override
  public String toString() {
    return events.toString();
  }
}

class ExamplePersistentActor extends AbstractPersistentActor {

  private ExampleState state = new ExampleState();
  private int snapShotInterval = 1000;

  public int getNumEvents() {
    return state.size();
  }

  @Override
  public String persistenceId() {
    return "sample-id-1";
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder()
        .match(Evt.class, state::update)
        .match(SnapshotOffer.class, ss -> state = (ExampleState) ss.snapshot())
        .build();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Cmd.class,
            c -> {
              final String data = c.getData();
              final Evt evt = new Evt(data + "-" + getNumEvents());
              persist(
                  evt,
                  (Evt e) -> {
                    state.update(e);
                    getContext().getSystem().getEventStream().publish(e);
                    if (lastSequenceNr() % snapShotInterval == 0 && lastSequenceNr() != 0)
                      // IMPORTANT: create a copy of snapshot because ExampleState is mutable
                      saveSnapshot(state.copy());
                  });
            })
        .matchEquals("print", s -> System.out.println(state))
        .build();
  }
}
// #persistent-actor-example

public class PersistentActorExample {
  public static void main(String... args) throws Exception {
    final ActorSystem system = ActorSystem.create("example");
    final ActorRef persistentActor =
        system.actorOf(Props.create(ExamplePersistentActor.class), "persistentActor-4-java8");
    persistentActor.tell(new Cmd("foo"), null);
    persistentActor.tell(new Cmd("baz"), null);
    persistentActor.tell(new Cmd("bar"), null);
    persistentActor.tell(new Cmd("buzz"), null);
    persistentActor.tell("print", null);

    Thread.sleep(10000);
    system.terminate();
  }
}
