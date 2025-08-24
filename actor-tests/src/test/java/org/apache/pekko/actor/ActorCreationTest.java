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

package org.apache.pekko.actor;

import static java.util.stream.Collectors.toCollection;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.stream.IntStream;
import org.apache.pekko.japi.function.Creator;
import org.junit.Assert;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class ActorCreationTest extends JUnitSuite {

  static class C implements Creator<AbstractActor> {
    @Override
    public AbstractActor create() throws Exception {
      return null;
    }
  }

  static class D<T> implements Creator<T> {
    @Override
    public T create() {
      return null;
    }
  }

  static class E<T extends AbstractActor> implements Creator<T> {
    @Override
    public T create() {
      return null;
    }
  }

  static interface I<T> extends Creator<AbstractActor> {}

  static class F implements I<Object> {
    @Override
    public AbstractActor create() {
      return null;
    }
  }

  static class G implements Creator {
    public Object create() {
      return null;
    }
  }

  abstract class H extends AbstractActor {
    public H(String a) {}
  }

  static class P implements Creator<AbstractActor> {
    final String value;

    public P(String value) {
      this.value = value;
    }

    @Override
    public AbstractActor create() throws Exception {
      return null;
    }
  }

  public static class TestActor extends AbstractActor {
    public static Props propsUsingLambda(Integer magicNumber) {
      // You need to specify the actual type of the returned actor
      // since Java 8 lambdas have some runtime type information erased
      return Props.create(TestActor.class, () -> new TestActor(magicNumber));
    }

    @SuppressWarnings("unused")
    private final Integer magicNumber;

    TestActor(Integer magicNumber) {
      this.magicNumber = magicNumber;
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder().build();
    }
  }

  public static class TestActor2 extends UntypedAbstractActor {

    public static Props propsUsingCreator(final int magicNumber) {
      // You need to specify the actual type of the returned actor
      // since runtime type information erased
      return Props.create(
          TestActor2.class,
          new Creator<TestActor2>() {
            private static final long serialVersionUID = 1L;

            @Override
            public TestActor2 create() throws Exception {
              return new TestActor2(magicNumber);
            }
          });
    }

    public static Props propsUsingCreatorWithoutClass(final int magicNumber) {
      return Props.create(
          TestActor2.class,
          new Creator<TestActor2>() {
            private static final long serialVersionUID = 1L;

            @Override
            public TestActor2 create() throws Exception {
              return new TestActor2(magicNumber);
            }
          });
    }

    private static final Creator<TestActor2> staticCreator =
        new Creator<TestActor2>() {
          private static final long serialVersionUID = 1L;

          @Override
          public TestActor2 create() throws Exception {
            return new TestActor2(12);
          }
        };

    public static Props propsUsingStaticCreator(final int magicNumber) {

      return Props.create(TestActor2.class, staticCreator);
    }

    final int magicNumber;

    TestActor2(int magicNumber) {
      this.magicNumber = magicNumber;
    }

    @Override
    public void onReceive(Object msg) {}
  }

  public static class Issue20537Reproducer extends UntypedAbstractActor {

    static final class ReproducerCreator implements Creator<Issue20537Reproducer> {

      final boolean create;

      private ReproducerCreator(boolean create) {
        this.create = create;
      }

      @Override
      public Issue20537Reproducer create() throws Exception {
        return new Issue20537Reproducer(create);
      }
    }

    public Issue20537Reproducer(boolean create) {}

    @Override
    public void onReceive(Object message) throws Exception {}
  }

  @Test
  public void testWrongAnonymousInPlaceCreator() {
    IllegalArgumentException exception =
        Assert.assertThrows(
            IllegalArgumentException.class,
            () ->
                Props.create(
                    Actor.class,
                    new Creator<Actor>() {
                      @Override
                      public Actor create() throws Exception {
                        return null;
                      }
                    }));
    assertEquals(
        "cannot use non-static local Creator to create actors; make it static (e.g. local to a"
            + " static method) or top-level",
        exception.getMessage());
  }

  @Test
  public void testRightTopLevelNonStaticCreator() {
    final Creator<UntypedAbstractActor> nonStatic = new NonStaticCreator();
    final Props p = Props.create(UntypedAbstractActor.class, nonStatic);
    assertEquals(UntypedAbstractActor.class, p.actorClass());
  }

  @Test
  public void testWrongAbstractActorClass() {
    IllegalArgumentException exception =
        Assert.assertThrows(IllegalArgumentException.class, () -> Props.create(H.class, "a"));
    assertEquals(
        String.format("Actor class [%s] must not be abstract", H.class.getName()),
        exception.getMessage());
  }

  private static Creator<AbstractActor> createAnonymousCreatorInStaticMethod() {
    return new Creator<AbstractActor>() {
      @Override
      public AbstractActor create() throws Exception {
        return null;
      }
    };
  }

  @Test
  public void testRightPropsUsingLambda() {
    final Props p = TestActor.propsUsingLambda(17);
    assertEquals(TestActor.class, p.actorClass());
  }

  @Test
  public void testRightPropsUsingCreator() {
    final Props p = TestActor2.propsUsingCreator(17);
    assertEquals(TestActor2.class, p.actorClass());
  }

  @Test
  public void testPropsUsingCreatorWithoutClass() {
    final Props p = TestActor2.propsUsingCreatorWithoutClass(17);
    assertEquals(TestActor2.class, p.actorClass());
  }

  @Test
  public void testIssue20537Reproducer() {
    final Issue20537Reproducer.ReproducerCreator creator =
        new Issue20537Reproducer.ReproducerCreator(false);
    final Props p = Props.create(Issue20537Reproducer.class, creator);
    assertEquals(Issue20537Reproducer.class, p.actorClass());

    ArrayList<Props> pList =
        IntStream.range(0, 4)
            .mapToObj(i -> Props.create(Issue20537Reproducer.class, creator))
            .collect(toCollection(ArrayList::new));
    for (Props each : pList) {
      assertEquals(Issue20537Reproducer.class, each.actorClass());
    }
  }
}
