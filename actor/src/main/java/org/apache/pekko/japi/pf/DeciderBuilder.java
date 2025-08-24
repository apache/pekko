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

package org.apache.pekko.japi.pf;

import static org.apache.pekko.actor.SupervisorStrategy.Directive;

import org.apache.pekko.japi.function.Function;
import org.apache.pekko.japi.function.Predicate;

/**
 * Used for building a partial function for {@link org.apache.pekko.actor.Actor#supervisorStrategy
 * Actor.supervisorStrategy()}. * Inside an actor you can use it like this with Java 8 to define
 * your supervisorStrategy.
 *
 * <p>Example:
 *
 * <pre>
 * &#64;Override
 * private static SupervisorStrategy strategy =
 *   new OneForOneStrategy(10, Duration.ofMinutes(1), DeciderBuilder.
 *     match(ArithmeticException.class, e -&gt; resume()).
 *     match(NullPointerException.class, e -&gt; restart()).
 *     match(IllegalArgumentException.class, e -&gt; stop()).
 *     matchAny(o -&gt; escalate()).build());
 *
 * &#64;Override
 * public SupervisorStrategy supervisorStrategy() {
 *   return strategy;
 * }
 * </pre>
 */
public class DeciderBuilder {
  private DeciderBuilder() {}

  /**
   * Return a new {@link PFBuilder} with a case statement added.
   *
   * @param type a type to match the argument against
   * @param apply an action to apply to the argument if the type matches
   * @return a builder with the case statement added
   */
  public static <P extends Throwable> PFBuilder<Throwable, Directive> match(
      final Class<P> type, Function<P, Directive> apply) {
    return Match.match(type, apply);
  }

  /**
   * Return a new {@link PFBuilder} with a case statement added.
   *
   * @param type a type to match the argument against
   * @param predicate a predicate that will be evaluated on the argument if the type matches
   * @param apply an action to apply to the argument if the type matches and the predicate returns
   *     true
   * @return a builder with the case statement added
   */
  public static <P extends Throwable> PFBuilder<Throwable, Directive> match(
      final Class<P> type, Predicate<P> predicate, Function<P, Directive> apply) {
    return Match.match(type, predicate, apply);
  }

  /**
   * Return a new {@link PFBuilder} with a case statement added.
   *
   * @param apply an action to apply to the argument
   * @return a builder with the case statement added
   */
  public static PFBuilder<Throwable, Directive> matchAny(Function<Throwable, Directive> apply) {
    return Match.matchAny(apply);
  }
}
