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

package org.apache.pekko.japi;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.pekko.japi.function.Function;
import org.apache.pekko.japi.function.Predicate;
import org.apache.pekko.japi.pf.Match;
import org.junit.jupiter.api.Test;
import scala.MatchError;

public class MatchBuilderTest {

  @Test
  public void shouldPassBasicMatchTest() {
    Match<Object, Double> pf =
        Match.create(
            Match.match(
                    Integer.class,
                    new Function<Integer, Double>() {
                      @Override
                      public Double apply(Integer integer) {
                        return integer * 10.0;
                      }
                    })
                .match(
                    Number.class,
                    new Function<Number, Double>() {
                      @Override
                      public Double apply(Number number) {
                        return number.doubleValue() * (-10.0);
                      }
                    }));

    org.junit.jupiter.api.Assertions.assertTrue(
        Double.valueOf(47110).equals(pf.match(Integer.valueOf(4711))),
        "An integer should be multiplied by 10");
    org.junit.jupiter.api.Assertions.assertTrue(
        Double.valueOf(-47110).equals(pf.match(Double.valueOf(4711))),
        "A double should be multiplied by -10");

    assertThrows(MatchError.class, () -> pf.match("4711"));
  }

  static class GenericClass<T> {
    T val;

    public GenericClass(T val) {
      this.val = val;
    }
  }

  @Test
  public void shouldHandleMatchOnGenericClass() {
    Match<Object, String> pf =
        Match.create(
            Match.matchUnchecked(
                GenericClass.class,
                new Function<GenericClass<String>, String>() {
                  @Override
                  public String apply(GenericClass<String> stringGenericClass) {
                    return stringGenericClass.val;
                  }
                }));

    org.junit.jupiter.api.Assertions.assertEquals(
        "A",
        pf.match(new GenericClass<>("A")),
        "String value should be extract from GenericMessage");
  }

  @Test
  public void shouldHandleMatchWithPredicateOnGenericClass() {
    Match<Object, String> pf =
        Match.create(
            Match.matchUnchecked(
                GenericClass.class,
                new Predicate<GenericClass<String>>() {
                  @Override
                  public boolean test(GenericClass<String> genericClass) {
                    return !genericClass.val.isEmpty();
                  }
                },
                new Function<GenericClass<String>, String>() {
                  @Override
                  public String apply(GenericClass<String> stringGenericClass) {
                    return stringGenericClass.val;
                  }
                }));

    assertThrows(MatchError.class, () -> pf.match(new GenericClass<>("")));
  }
}
