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

package jdocs.stream;

import org.apache.pekko.actor.ActorRef;

import java.util.function.Predicate;

/**
 * Acts as if `System.out.println()` yet swallows all messages. Useful for putting printlines in
 * examples yet without polluting the build with them.
 */
public class SilenceSystemOut {

  private SilenceSystemOut() {}

  public static System get() {
    return new System(
        new System.Println() {
          @Override
          public void println(String s) {
            // ignore
          }
        });
  }

  public static System get(ActorRef probe) {
    return new System(
        new System.Println() {
          @Override
          public void println(String s) {
            probe.tell(s, ActorRef.noSender());
          }
        });
  }

  public static System get(Predicate<String> filter, ActorRef probe) {
    return new System(
        new System.Println() {
          @Override
          public void println(String s) {
            if (filter.test(s)) probe.tell(s, ActorRef.noSender());
          }
        });
  }

  public static class System {
    public final Println out;

    public System(Println out) {
      this.out = out;
    }

    public abstract static class Println {
      public abstract void println(String s);

      public void println(Object s) {
        println(s.toString());
      }

      public void printf(String format, Object... args) {
        println(String.format(format, args));
      }
    }
  }
}
