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

import static org.junit.Assert.*;

import org.apache.pekko.event.Logging;
import org.apache.pekko.event.Logging.LoggerInitialized;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.japi.function.Creator;
import org.apache.pekko.japi.tuple.Tuple22;
import org.apache.pekko.japi.tuple.Tuple4;
import org.apache.pekko.routing.FromConfig;
import org.apache.pekko.routing.GetRoutees;
import org.apache.pekko.routing.NoRouter;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.testkit.TestProbe;
import org.junit.ClassRule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class JavaAPI extends JUnitSuite {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("JavaAPI", PekkoSpec.testConf());

  private final ActorSystem system = actorSystemResource.getSystem();

  // compilation tests
  @SuppressWarnings("unused")
  public void mustCompile() {
    final Kill kill = Kill.getInstance();
    final PoisonPill pill = PoisonPill.getInstance();
    final ReceiveTimeout t = ReceiveTimeout.getInstance();

    final LocalScope ls = LocalScope.getInstance();
    final NoScopeGiven noscope = NoScopeGiven.getInstance();

    final LoggerInitialized x = Logging.loggerInitialized();

    final GetRoutees r = GetRoutees.getInstance();
    final NoRouter nr = NoRouter.getInstance();
    final FromConfig fc = FromConfig.getInstance();

    final ActorPath p = ActorPaths.fromString("pekko://Sys@localhost:1234/user/abc");
  }

  @Test
  public void mustBeAbleToCreateActorRefFromClass() {
    ActorRef ref = system.actorOf(Props.create(JavaAPITestActor.class));
    assertNotNull(ref);
  }

  @SuppressWarnings("unchecked")
  public static Props mkErasedProps() {
    return Props.create(
        JavaAPITestActor.class,
        new Creator() {
          public Object create() {
            return new JavaAPITestActor();
          }
        });
  }

  public static Props mkPropsWithLambda() {
    return Props.create(JavaAPITestActor.class, JavaAPITestActor::new);
  }

  @Test
  public void mustBeAbleToCreateActorRefFromFactory() {
    ActorRef ref = system.actorOf(mkPropsWithLambda());
    assertNotNull(ref);
  }

  @Test
  public void mustBeAbleToCreateActorRefFromErasedFactory() {
    ActorRef ref = system.actorOf(mkErasedProps());
    assertNotNull(ref);
  }

  @Test
  public void mustBeAbleToCreateActorWIthConstructorParams() {
    ActorRef ref =
        system.actorOf(
            Props.create(ActorWithConstructorParams.class, "a", "b", Integer.valueOf(17), 18));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "get");
    probe.expectMsg("a-b-17-18");
  }

  @Test
  public void mustBeAbleToCreateActorWIthBoxedAndUnBoxedConstructorParams() {
    ActorRef ref =
        system.actorOf(
            Props.create(ActorWithConstructorParams.class, "a", "b", 17, Integer.valueOf(18)));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "get");
    probe.expectMsg("a-b-17-18");
  }

  @Test
  public void mustBeAbleToCreateActorWIthNullConstructorParams() {
    ActorRef ref =
        system.actorOf(Props.create(ActorWithConstructorParams.class, "a", null, null, 18));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "get");
    probe.expectMsg("a-null-null-18");
  }

  @Test
  public void mustBeAbleToCreateActorWIthNullConstructorParams2() {
    // without this Object array wrapper it will not compile: "reference to create is ambiguous"
    ActorRef ref =
        system.actorOf(Props.create(ActorWithConstructorParams.class, new Object[] {null}));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "get");
    probe.expectMsg("null-undefined-0-0");
  }

  @Test
  public void mustBeAbleToCreateActorWIthNullConstructorParams3() {
    ActorRef ref = system.actorOf(Props.create(ActorWithConstructorParams.class, "a", null));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "get");
    probe.expectMsg("a-null-0-0");
  }

  @Test
  @SuppressWarnings("unused")
  public void mustCompileTupleCreation() {
    final Pair<Integer, String> p = Pair.create(1, "2");
    final Tuple4<Integer, String, Integer, Long> t4 = Tuple4.create(1, "2", 3, 4L);
    Tuple22.create(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
  }

  public static class ActorWithConstructorParams extends UntypedAbstractActor {

    private final String a;
    private final String b;
    private final Integer c;
    private final int d;

    public ActorWithConstructorParams(String a, String b, Integer c, int d) {
      this.a = a;
      this.b = b;
      this.c = c;
      this.d = d;
    }

    public ActorWithConstructorParams(String a, Object b) {
      this.a = a;
      this.b = String.valueOf(b);
      this.c = 0;
      this.d = 0;
    }

    public ActorWithConstructorParams(String a, int d) {
      this.a = a;
      this.b = "undefined";
      this.c = 0;
      this.d = d;
    }

    public ActorWithConstructorParams(Object a) {
      this.a = String.valueOf(a);
      this.b = "undefined";
      this.c = 0;
      this.d = 0;
    }

    public ActorWithConstructorParams(int d) {
      this.a = "undefined";
      this.b = "undefined";
      this.c = 0;
      this.d = d;
    }

    public void onReceive(Object msg) {
      String reply = a + "-" + b + "-" + c + "-" + d;
      getSender().tell(reply, getSelf());
    }
  }
}
