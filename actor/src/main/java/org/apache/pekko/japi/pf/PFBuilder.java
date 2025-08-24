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

import org.apache.pekko.japi.function.Function;
import org.apache.pekko.japi.function.Predicate;

/**
 * A builder for {@link scala.PartialFunction}.
 *
 * @param <I> the input type, that this PartialFunction will be applied to
 * @param <R> the return type, that the results of the application will have
 */
public final class PFBuilder<I, R> extends AbstractPFBuilder<I, R> {

  /**
   * Create a new {@link PFBuilder}.
   *
   * @since 1.1.0
   */
  public static <I, R> PFBuilder<I, R> create() {
    return new PFBuilder<>();
  }

  /** Create a PFBuilder. */
  public PFBuilder() {}

  /**
   * Add a new case statement to this builder.
   *
   * @param type a type to match the argument against
   * @param apply an action to apply to the argument if the type matches
   * @return a builder with the case statement added
   */
  public <P> PFBuilder<I, R> match(final Class<P> type, Function<P, R> apply) {
    return matchUnchecked(type, apply);
  }

  /**
   * Add a new case statement to this builder without compile time type check of the parameters.
   * Should normally not be used, but when matching on class with generic type argument it can be
   * useful, e.g. <code>List.class</code> and <code>(List&lt;String&gt; list) -> {}</code>.
   *
   * @param type a type to match the argument against
   * @param apply an action to apply to the argument if the type matches
   * @return a builder with the case statement added
   */
  @SuppressWarnings("unchecked")
  public PFBuilder<I, R> matchUnchecked(final Class<?> type, Function<?, R> apply) {
    Predicate<I> predicate = type::isInstance;
    addStatement(new CaseStatement<I, Object, R>(predicate, (Function<Object, R>) apply));
    return this;
  }

  /**
   * Add a new case statement to this builder.
   *
   * @param type a type to match the argument against
   * @param predicate a predicate that will be evaluated on the argument if the type matches
   * @param apply an action to apply to the argument if the type matches and the predicate returns
   *     true
   * @return a builder with the case statement added
   */
  public <P> PFBuilder<I, R> match(
      final Class<P> type, final Predicate<P> predicate, final Function<P, R> apply) {
    return matchUnchecked(type, predicate, apply);
  }

  /**
   * Add a new case statement to this builder without compile time type check of the parameters.
   * Should normally not be used, but when matching on class with generic type argument it can be
   * useful, e.g. <code>List.class</code> and <code>(List&lt;String&gt; list) -> {}</code>.
   *
   * @param type a type to match the argument against
   * @param predicate a predicate that will be evaluated on the argument if the type matches
   * @param apply an action to apply to the argument if the type matches and the predicate returns
   *     true
   * @return a builder with the case statement added
   */
  @SuppressWarnings("unchecked")
  public PFBuilder<I, R> matchUnchecked(
      final Class<?> type, final Predicate<?> predicate, final Function<?, R> apply) {
    Predicate<I> fiPredicate =
        o -> {
          if (!type.isInstance(o)) {
            return false;
          } else {
            return ((Predicate<Object>) predicate).test(o);
          }
        };
    addStatement(new CaseStatement<I, Object, R>(fiPredicate, (Function<Object, R>) apply));
    return this;
  }

  /**
   * Add a new case statement to this builder.
   *
   * @param object the object to compare equals with
   * @param apply an action to apply to the argument if the object compares equal
   * @return a builder with the case statement added
   */
  public <P> PFBuilder<I, R> matchEquals(final P object, final Function<P, R> apply) {
    addStatement(
        new CaseStatement<I, P, R>(
            new Predicate<>() {
              @Override
              public boolean test(final I param) {
                return object.equals(param);
              }
            },
            apply));
    return this;
  }

  /**
   * Add a new case statement to this builder, that matches any argument.
   *
   * @param apply an action to apply to the argument
   * @return a builder with the case statement added
   */
  public PFBuilder<I, R> matchAny(final Function<I, R> apply) {
    addStatement(
        new CaseStatement<I, I, R>(
            new Predicate<I>() {
              @Override
              public boolean test(final I param) {
                return true;
              }
            },
            apply));
    return this;
  }
}
