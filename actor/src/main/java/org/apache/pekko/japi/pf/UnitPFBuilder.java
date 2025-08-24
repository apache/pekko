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

import org.apache.pekko.japi.function.Predicate;
import org.apache.pekko.japi.function.Procedure;
import scala.runtime.BoxedUnit;

/**
 * A builder for {@link scala.PartialFunction}. This is a specialized version of {@link PFBuilder}
 * to map java void methods to {@link scala.runtime.BoxedUnit}.
 *
 * @param <I> the input type, that this PartialFunction to be applied to
 */
public final class UnitPFBuilder<I> extends AbstractPFBuilder<I, BoxedUnit> {

  /** Create a UnitPFBuilder. */
  public UnitPFBuilder() {}

  /**
   * Add a new case statement to this builder.
   *
   * @param type a type to match the argument against
   * @param apply an action to apply to the argument if the type matches
   * @return a builder with the case statement added
   */
  public <P> UnitPFBuilder<I> match(final Class<P> type, final Procedure<P> apply) {
    return matchUnchecked(type, apply);
  }

  /**
   * Add a new case statement to this builder without compile time type check. Should normally not
   * be used, but when matching on class with generic type argument it can be useful, e.g. <code>
   * List.class</code> and <code>(List&lt;String&gt; list) -> {}</code>.
   *
   * @param type a type to match the argument against
   * @param apply an action to apply to the argument if the type matches
   * @return a builder with the case statement added
   */
  @SuppressWarnings("unchecked")
  public UnitPFBuilder<I> matchUnchecked(final Class<?> type, final Procedure<?> apply) {

    Predicate<I> predicate =
        new Predicate<I>() {
          @Override
          public boolean test(final I param) {
            return type.isInstance(param);
          }
        };

    addStatement(new UnitCaseStatement<I, Object>(predicate, (Procedure<Object>) apply));

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
  public <P> UnitPFBuilder<I> match(
      final Class<P> type, final Predicate<P> predicate, final Procedure<P> apply) {
    return matchUnchecked(type, predicate, apply);
  }

  /**
   * Add a new case statement to this builder without compile time type check. Should normally not
   * be used, but when matching on class with generic type argument it can be useful, e.g. <code>
   * List.class</code> and <code>(List&lt;String&gt; list) -> {}</code>.
   *
   * @param type a type to match the argument against
   * @param predicate a predicate that will be evaluated on the argument if the type matches
   * @param apply an action to apply to the argument if the type matches and the predicate returns
   *     true
   * @return a builder with the case statement added
   */
  @SuppressWarnings("unchecked")
  public UnitPFBuilder<I> matchUnchecked(
      final Class<?> type, final Predicate<?> predicate, final Procedure<?> apply) {
    Predicate<I> fiPredicate =
        new Predicate<I>() {
          @Override
          public boolean test(final I param) {
            if (!type.isInstance(param)) {
              return false;
            } else {
              return ((Predicate<Object>) predicate).test(param);
            }
          }
        };

    addStatement(new UnitCaseStatement<I, Object>(fiPredicate, (Procedure<Object>) apply));

    return this;
  }

  /**
   * Add a new case statement to this builder.
   *
   * @param object the object to compare equals with
   * @param apply an action to apply to the argument if the object compares equal
   * @return a builder with the case statement added
   */
  public <P> UnitPFBuilder<I> matchEquals(final P object, final Procedure<P> apply) {
    addStatement(
        new UnitCaseStatement<I, P>(
            new Predicate<I>() {
              @Override
              public boolean test(final I param) {
                return object.equals(param);
              }
            },
            apply));
    return this;
  }

  /**
   * Add a new case statement to this builder.
   *
   * @param object the object to compare equals with
   * @param predicate a predicate that will be evaluated on the argument if the object compares
   *     equal
   * @param apply an action to apply to the argument if the object compares equal
   * @return a builder with the case statement added
   */
  public <P> UnitPFBuilder<I> matchEquals(
      final P object, final Predicate<P> predicate, final Procedure<P> apply) {
    addStatement(
        new UnitCaseStatement<I, P>(
            new Predicate<I>() {
              @Override
              public boolean test(final I param) {
                if (!object.equals(param)) {
                  return false;
                } else {
                  @SuppressWarnings("unchecked")
                  P p = (P) param;
                  return predicate.test(p);
                }
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
  public UnitPFBuilder<I> matchAny(final Procedure<Object> apply) {
    addStatement(
        new UnitCaseStatement<I, Object>(
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
