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

package jdocs.remoting;

import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import jdocs.AbstractJavaTest;
import org.junit.ClassRule;
import org.junit.Test;

import org.ekrich.config.ConfigFactory;

// #import
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Address;
import org.apache.pekko.actor.AddressFromURIString;
import org.apache.pekko.actor.Deploy;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.remote.RemoteScope;
// #import

import org.apache.pekko.actor.AbstractActor;

import static org.junit.Assert.assertEquals;

public class RemoteDeploymentDocTest extends AbstractJavaTest {

  public static class SampleActor extends AbstractActor {
    @Override
    public Receive createReceive() {
      return receiveBuilder().matchAny(message -> getSender().tell(getSelf(), getSelf())).build();
    }
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource(
          "RemoteDeploymentDocTest",
          ConfigFactory.parseString(
                  "   pekko.actor.provider = remote\n"
                      + "    pekko.remote.classic.netty.tcp.port = 0\n"
                      + "    pekko.remote.artery.canonical.port = 0\n"
                      + "    pekko.remote.use-unsafe-remote-features-outside-cluster = on")
              .withFallback(PekkoSpec.testConf()));

  private final ActorSystem system = actorSystemResource.getSystem();

  @SuppressWarnings("unused")
  void makeAddress() {
    // #make-address-artery
    Address addr = new Address("pekko", "sys", "host", 1234);
    addr = AddressFromURIString.parse("pekko://sys@host:1234"); // the same
    // #make-address-artery
  }

  @Test
  public void demonstrateDeployment() {
    // #make-address
    Address addr = new Address("pekko", "sys", "host", 1234);
    addr = AddressFromURIString.parse("pekko://sys@host:1234"); // the same
    // #make-address
    // #deploy
    Props props = Props.create(SampleActor.class).withDeploy(new Deploy(new RemoteScope(addr)));
    ActorRef ref = system.actorOf(props);
    // #deploy
    assertEquals(addr, ref.path().address());
  }

  @Test
  public void demonstrateSampleActor() {
    // #sample-actor

    ActorRef actor = system.actorOf(Props.create(SampleActor.class), "sampleActor");
    actor.tell("Pretty slick", ActorRef.noSender());
    // #sample-actor
  }

  @Test
  public void demonstrateProgrammaticConfig() {
    // #programmatic
    ConfigFactory.parseString("pekko.remote.classic.netty.tcp.hostname=\"1.2.3.4\"")
        .withFallback(ConfigFactory.load());
    // #programmatic

    // #programmatic-artery
    ConfigFactory.parseString("pekko.remote.artery.canonical.hostname=\"1.2.3.4\"")
        .withFallback(ConfigFactory.load());
    // #programmatic-artery
  }
}
