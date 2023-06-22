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

package jdocs.actor.fsm;

import org.apache.pekko.actor.ActorRef;
import java.util.List;

public class Events {

  public
  // #simple-events
  static final class SetTarget {
    private final ActorRef ref;

    public SetTarget(ActorRef ref) {
      this.ref = ref;
    }

    public ActorRef getRef() {
      return ref;
    }
    // #boilerplate

    @Override
    public String toString() {
      return "SetTarget{" + "ref=" + ref + '}';
    }
    // #boilerplate
  }

  // #simple-events
  public
  // #simple-events
  static final class Queue {
    private final Object obj;

    public Queue(Object obj) {
      this.obj = obj;
    }

    public Object getObj() {
      return obj;
    }
    // #boilerplate

    @Override
    public String toString() {
      return "Queue{" + "obj=" + obj + '}';
    }
    // #boilerplate
  }

  // #simple-events
  public
  // #simple-events
  static final class Batch {
    private final List<Object> list;

    public Batch(List<Object> list) {
      this.list = list;
    }

    public List<Object> getList() {
      return list;
    }
    // #boilerplate

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Batch batch = (Batch) o;

      return list.equals(batch.list);
    }

    @Override
    public int hashCode() {
      return list.hashCode();
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append("Batch{list=");
      list.stream()
          .forEachOrdered(
              e -> {
                builder.append(e);
                builder.append(",");
              });
      int len = builder.length();
      builder.replace(len, len, "}");
      return builder.toString();
    }
    // #boilerplate
  }

  // #simple-events
  public
  // #simple-events
  static enum Flush {
    Flush
  }
  // #simple-events
}
