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

/**
 * Class that encapsulates Functional Interfaces used for creating partial functions.
 *
 * <p>These classes are kept for compatibility, but for future API's please prefer the ones in
 * {@link org.apache.pekko.japi.function}.
 */
public final class FI {
  private FI() {}

  /**
   * Functional interface for an application.
   *
   * <p>This class is kept for compatibility, but for future API's please prefer {@link
   * org.apache.pekko.japi.function.Function}.
   *
   * @param <I> the input type, that this Apply will be applied to
   * @param <R> the return type, that the results of the application will have
   * @deprecated Will be removed in 2.0.0
   */
  @FunctionalInterface
  @Deprecated
  public interface Apply<I, R> {
    /**
     * The application to perform.
     *
     * @param i an instance that the application is performed on
     * @return the result of the application
     * @deprecated Will be removed in 2.0.0
     */
    R apply(I i) throws Exception;
  }

  /**
   * Functional interface for an application.
   *
   * <p>This class is kept for compatibility, but for future API's please prefer {@link
   * org.apache.pekko.japi.function.Function2}.
   *
   * @param <I1> the first input type, that this Apply will be applied to
   * @param <I2> the second input type, that this Apply will be applied to
   * @param <R> the return type, that the results of the application will have
   * @deprecated Will be removed in 2.0.0
   */
  @FunctionalInterface
  @Deprecated
  public interface Apply2<I1, I2, R> {
    /**
     * The application to perform.
     *
     * @param i1 an instance that the application is performed on
     * @param i2 an instance that the application is performed on
     * @return the result of the application
     * @deprecated Will be removed in 2.0.0
     */
    R apply(I1 i1, I2 i2) throws Exception;
  }

  /**
   * Functional interface for a predicate.
   *
   * <p>This class is kept for compatibility, but for future API's please prefer {@link
   * java.util.function.Predicate} or {@link org.apache.pekko.japi.function.Predicate}.
   *
   * @param <T> the type that the predicate will operate on.
   * @deprecated Will be removed in 2.0.0
   */
  @Deprecated
  @FunctionalInterface
  public interface TypedPredicate<T> {
    /**
     * The predicate to evaluate.
     *
     * @param t an instance that the predicate is evaluated on.
     * @return the result of the predicate
     */
    boolean defined(T t);
  }

  /**
   * Functional interface for a predicate.
   *
   * @param <T> the type that the predicate will operate on.
   * @param <U> the type that the predicate will operate on.
   * @deprecated Will be removed in 2.0.0
   */
  @FunctionalInterface
  @Deprecated
  public interface TypedPredicate2<T, U> {
    /**
     * The predicate to evaluate.
     *
     * @param t an instance that the predicate is evaluated on.
     * @param u an instance that the predicate is evaluated on.
     * @return the result of the predicate
     */
    boolean defined(T t, U u);
  }

  /**
   * Functional interface for an application.
   *
   * <p>This class is kept for compatibility, but for future API's please prefer {@link
   * org.apache.pekko.japi.function.Procedure}.
   *
   * @param <I> the input type, that this Apply will be applied to
   * @deprecated Will be removed in 2.0.0
   */
  @FunctionalInterface
  @Deprecated
  public interface UnitApply<I> {
    /**
     * The application to perform.
     *
     * @param i an instance that the application is performed on
     */
    void apply(I i) throws Exception;
  }

  /**
   * Functional interface for an application.
   *
   * @param <I1> the first input type, that this Apply will be applied to
   * @param <I2> the second input type, that this Apply will be applied to
   * @deprecated Will be removed in 2.0.0
   */
  @FunctionalInterface
  @Deprecated
  public interface UnitApply2<I1, I2> {
    /**
     * The application to perform.
     *
     * @param i1 an instance that the application is performed on
     * @param i2 an instance that the application is performed on
     */
    void apply(I1 i1, I2 i2) throws Exception;
  }

  /**
   * Functional interface for an application.
   *
   * @param <I1> the first input type, that this Apply will be applied to
   * @param <I2> the second input type, that this Apply will be applied to
   * @param <I3> the third input type, that this Apply will be applied to
   * @deprecated Will be removed in 2.0.0
   */
  @FunctionalInterface
  @Deprecated
  public interface UnitApply3<I1, I2, I3> {
    /**
     * The application to perform.
     *
     * @param i1 an instance that the application is performed on
     * @param i2 an instance that the application is performed on
     * @param i3 an instance that the application is performed on
     */
    void apply(I1 i1, I2 i2, I3 i3) throws Exception;
  }

  /**
   * Functional interface for an application.
   *
   * @param <I1> the first input type, that this Apply will be applied to
   * @param <I2> the second input type, that this Apply will be applied to
   * @param <I3> the third input type, that this Apply will be applied to
   * @param <I4> the fourth input type, that this Apply will be applied to
   * @deprecated Will be removed in 2.0.0
   */
  @FunctionalInterface
  @Deprecated
  public interface UnitApply4<I1, I2, I3, I4> {
    /**
     * The application to perform.
     *
     * @param i1 an instance that the application is performed on
     * @param i2 an instance that the application is performed on
     * @param i3 an instance that the application is performed on
     * @param i4 an instance that the application is performed on
     */
    void apply(I1 i1, I2 i2, I3 i3, I4 i4) throws Exception;
  }

  /**
   * Functional interface for an application.
   *
   * <p>This class is kept for compatibility, but for future API's please prefer {@link
   * org.apache.pekko.japi.function.Effect}.
   *
   * @deprecated Will be removed in 2.0.0
   */
  @FunctionalInterface
  @Deprecated
  public interface UnitApplyVoid {
    /** The application to perform. */
    void apply() throws Exception;
  }

  /**
   * Package scoped functional interface for a predicate. Used internally to match against arbitrary
   * types.
   *
   * <p>This class is kept for compatibility, but for future API's please prefer {@link
   * java.util.function.Predicate} or {@link org.apache.pekko.japi.function.Predicate}.
   *
   * @deprecated Will be removed in 2.0.0
   */
  @FunctionalInterface
  @Deprecated
  interface Predicate {
    /**
     * The predicate to evaluate.
     *
     * @param o an instance that the predicate is evaluated on.
     * @return the result of the predicate
     */
    boolean defined(Object o);
  }
}
