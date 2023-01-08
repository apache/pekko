/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2013-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.io.japi;

// #message
public class Message {

  public static class Person {
    private final String first;
    private final String last;

    public Person(String first, String last) {
      this.first = first;
      this.last = last;
    }

    public String getFirst() {
      return first;
    }

    public String getLast() {
      return last;
    }
  }

  private final Person[] persons;
  private final double[] happinessCurve;

  public Message(Person[] persons, double[] happinessCurve) {
    this.persons = persons;
    this.happinessCurve = happinessCurve;
  }

  public Person[] getPersons() {
    return persons;
  }

  public double[] getHappinessCurve() {
    return happinessCurve;
  }
}
// #message
