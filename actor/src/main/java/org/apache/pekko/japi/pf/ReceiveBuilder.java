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

import org.apache.pekko.actor.AbstractActor.Receive;
import org.apache.pekko.japi.function.Predicate;
import org.apache.pekko.japi.function.Procedure;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * Used for building a partial function for {@link
 * org.apache.pekko.actor.AbstractActor#createReceive AbstractActor.createReceive()}.
 *
 * <p>There is both a match on type only, and a match on type and predicate.
 *
 * <p>Inside an actor you can use it like this:
 *
 * <p>Example:
 *
 * <pre>
 * &#64;Override
 * public Receive createReceive() {
 *   return receiveBuilder()
 *     .match(Double.class, d -&gt; {
 *       getSender().tell(d.isNaN() ? 0 : d, self());
 *     })
 *     .match(Integer.class, i -&gt; {
 *       getSender().tell(i * 10, self());
 *     })
 *     .match(String.class, s -&gt; s.startsWith("foo"), s -&gt; {
 *       getSender().tell(s.toUpperCase(), self());
 *     })
 *     .build()
 *   );
 * }
 * </pre>
 */
public class ReceiveBuilder {

  private PartialFunction<Object, BoxedUnit> statements = null;

  protected void addStatement(PartialFunction<Object, BoxedUnit> statement) {
    if (statements == null) statements = statement;
    else statements = statements.orElse(statement);
  }

  /**
   * Build a {@link scala.PartialFunction} from this builder. After this call the builder will be
   * reset.
   *
   * @return a PartialFunction for this builder.
   */
  public Receive build() {
    PartialFunction<Object, BoxedUnit> empty = CaseStatement.empty();

    if (statements == null) return new Receive(empty);
    else return new Receive(statements.orElse(empty)); // FIXME why no new Receive(statements)?
  }

  /**
   * Return a new {@link ReceiveBuilder} with no case statements. They can be added later as the
   * returned {@link ReceiveBuilder} is a mutable object.
   *
   * @return a builder with no case statements
   */
  public static ReceiveBuilder create() {
    return new ReceiveBuilder();
  }

  /**
   * Add a new case statement to this builder.
   *
   * @param type a type to match the argument against
   * @param apply an action to apply to the argument if the type matches
   * @return a builder with the case statement added
   */
  public <P> ReceiveBuilder match(final Class<P> type, final Procedure<P> apply) {
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
  public ReceiveBuilder matchUnchecked(final Class<?> type, final Procedure<?> apply) {

    Predicate<Object> predicate =
        new Predicate<>() {
          @Override
          public boolean test(Object o) {
            return type.isInstance(o);
          }
        };

    addStatement(new UnitCaseStatement<Object, Object>(predicate, (Procedure<Object>) apply));

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
  public <P> ReceiveBuilder match(
      final Class<P> type, final Predicate<P> predicate, final Procedure<P> apply) {
    return matchUnchecked(type, predicate, apply);
  }

  /**
   * Add a new case statement to this builder.
   *
   * @param type a type to match the argument against
   * @param externalPredicate a external predicate that will be evaluated if the type matches
   * @param apply an action to apply to the argument if the type matches and the predicate returns
   *     true
   * @return a builder with the case statement added
   */
  public <P> ReceiveBuilder match(
      final Class<P> type,
      final java.util.function.BooleanSupplier externalPredicate,
      final Procedure<P> apply) {
    return matchUnchecked(type, externalPredicate, apply);
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
  public <P> ReceiveBuilder matchUnchecked(
      final Class<?> type, final Predicate<?> predicate, final Procedure<P> apply) {
    Predicate<Object> fiPredicate =
        new Predicate<>() {
          @Override
          public boolean test(Object o) {
            if (!type.isInstance(o)) return false;
            else return ((Predicate<Object>) predicate).test(o);
          }
        };

    addStatement(new UnitCaseStatement<Object, Object>(fiPredicate, (Procedure<Object>) apply));

    return this;
  }

  /**
   * Add a new case statement to this builder without compile time type check. Should normally not
   * be used, but when matching on class with generic type argument it can be useful, e.g. <code>
   * List.class</code> and <code>(List&lt;String&gt; list) -> {}</code>.
   *
   * @param type a type to match the argument against
   * @param externalPredicate an external predicate that will be evaluated if the type matches
   * @param apply an action to apply to the argument if the type matches and the predicate returns
   *     true
   * @return a builder with the case statement added
   */
  @SuppressWarnings("unchecked")
  public <P> ReceiveBuilder matchUnchecked(
      final Class<?> type,
      final java.util.function.BooleanSupplier externalPredicate,
      final Procedure<P> apply) {
    Predicate<Object> fiPredicate =
        new Predicate<>() {
          @Override
          public boolean test(Object o) {
            return type.isInstance(o) && externalPredicate.getAsBoolean();
          }
        };

    addStatement(new UnitCaseStatement<Object, Object>(fiPredicate, (Procedure<Object>) apply));

    return this;
  }

  /**
   * Add a new case statement to this builder.
   *
   * @param object the object to compare equals with
   * @param apply an action to apply to the argument if the object compares equal
   * @return a builder with the case statement added
   */
  public <P> ReceiveBuilder matchEquals(final P object, final Procedure<P> apply) {
    addStatement(
        new UnitCaseStatement<Object, P>(
            new Predicate<>() {
              @Override
              public boolean test(Object o) {
                return object.equals(o);
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
  public <P> ReceiveBuilder matchEquals(
      final P object, final Predicate<P> predicate, final Procedure<P> apply) {
    addStatement(
        new UnitCaseStatement<Object, P>(
            new Predicate<>() {
              @Override
              public boolean test(Object o) {
                if (!object.equals(o)) return false;
                else {
                  @SuppressWarnings("unchecked")
                  P p = (P) o;
                  return predicate.test(p);
                }
              }
            },
            apply));
    return this;
  }

  /**
   * Add a new case statement to this builder.
   *
   * @param object the object to compare equals with
   * @param externalPredicate an external predicate that will be evaluated if the object compares
   *     equal
   * @param apply an action to apply to the argument if the object compares equal
   * @return a builder with the case statement added
   */
  @SuppressWarnings("unchecked")
  public <P> ReceiveBuilder matchEquals(
      final P object,
      final java.util.function.BooleanSupplier externalPredicate,
      final Procedure<P> apply) {
    final Predicate<Object> predicate = o -> object.equals(o) && externalPredicate.getAsBoolean();
    addStatement(new UnitCaseStatement<>(predicate, (Procedure<Object>) apply));
    return this;
  }

  private static final Predicate<Object> ALWAYS_TRUE = (input) -> true;

  /**
   * Add a new case statement to this builder, that matches any argument.
   *
   * @param apply an action to apply to the argument
   * @return a builder with the case statement added
   */
  public ReceiveBuilder matchAny(final Procedure<Object> apply) {
    addStatement(new UnitCaseStatement<>(ALWAYS_TRUE, apply));
    return this;
  }

  /**
   * Add a new case statement to this builder, that pass the test of the predicate.
   *
   * @param externalPredicate an external predicate that will always be evaluated.
   * @param apply an action to apply to the argument
   * @return a builder with the case statement added
   */
  public ReceiveBuilder matchAny(
      final java.util.function.BooleanSupplier externalPredicate, final Procedure<Object> apply) {
    final Predicate<Object> predicate = o -> externalPredicate.getAsBoolean();
    addStatement(new UnitCaseStatement<>(predicate, apply));
    return this;
  }
}
