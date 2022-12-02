/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.remoting;

import org.apache.pekko.testkit.AkkaJUnitActorSystemResource;
import org.apache.pekko.testkit.AkkaSpec;
import jdocs.AbstractJavaTest;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

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
  public static AkkaJUnitActorSystemResource actorSystemResource =
      new AkkaJUnitActorSystemResource(
          "RemoteDeploymentDocTest",
          ConfigFactory.parseString(
                  "   pekko.actor.provider = remote\n"
                      + "    pekko.remote.classic.netty.tcp.port = 0\n"
                      + "    pekko.remote.artery.canonical.port = 0\n"
                      + "    pekko.remote.use-unsafe-remote-features-outside-cluster = on")
              .withFallback(AkkaSpec.testConf()));

  private final ActorSystem system = actorSystemResource.getSystem();

  @SuppressWarnings("unused")
  void makeAddress() {
    // #make-address-artery
    Address addr = new Address("akka", "sys", "host", 1234);
    addr = AddressFromURIString.parse("akka://sys@host:1234"); // the same
    // #make-address-artery
  }

  @Test
  public void demonstrateDeployment() {
    // #make-address
    Address addr = new Address("akka", "sys", "host", 1234);
    addr = AddressFromURIString.parse("akka://sys@host:1234"); // the same
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
