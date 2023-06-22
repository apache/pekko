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

package jdocs.routing;

import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;

import jdocs.AbstractJavaTest;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.ClassRule;
import org.junit.Test;

import org.apache.pekko.actor.ActorSystem;

// #imports1
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.routing.ConsistentHashingRouter.ConsistentHashable;

import java.util.Map;
import java.util.HashMap;
import java.io.Serializable;
// #imports1

// #imports2
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.routing.ConsistentHashingPool;
import org.apache.pekko.routing.ConsistentHashingRouter.ConsistentHashMapper;
import org.apache.pekko.routing.ConsistentHashingRouter.ConsistentHashableEnvelope;
// #imports2

public class ConsistentHashingRouterDocTest extends AbstractJavaTest {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("ConsistentHashingRouterDocTest");

  private final ActorSystem system = actorSystemResource.getSystem();

  public
  // #cache-actor
  static class Cache extends AbstractActor {
    Map<String, String> cache = new HashMap<String, String>();

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              Entry.class,
              entry -> {
                cache.put(entry.key, entry.value);
              })
          .match(
              Get.class,
              get -> {
                Object value = cache.get(get.key);
                getSender().tell(value == null ? NOT_FOUND : value, getSelf());
              })
          .match(
              Evict.class,
              evict -> {
                cache.remove(evict.key);
              })
          .build();
    }
  }

  // #cache-actor
  public
  // #cache-actor
  static final class Evict implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String key;

    public Evict(String key) {
      this.key = key;
    }
  }

  // #cache-actor
  public
  // #cache-actor
  static final class Get implements Serializable, ConsistentHashable {
    private static final long serialVersionUID = 1L;
    public final String key;

    public Get(String key) {
      this.key = key;
    }

    public Object consistentHashKey() {
      return key;
    }
  }

  // #cache-actor
  public
  // #cache-actor
  static final class Entry implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String key;
    public final String value;

    public Entry(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }

  // #cache-actor
  public
  // #cache-actor
  static final String NOT_FOUND = "NOT_FOUND";
  // #cache-actor

  @Test
  public void demonstrateUsageOfConsistentHashableRouter() {

    new TestKit(system) {
      {

        // #consistent-hashing-router

        final ConsistentHashMapper hashMapper =
            new ConsistentHashMapper() {
              @Override
              public Object hashKey(Object message) {
                if (message instanceof Evict) {
                  return ((Evict) message).key;
                } else {
                  return null;
                }
              }
            };

        ActorRef cache =
            system.actorOf(
                new ConsistentHashingPool(10)
                    .withHashMapper(hashMapper)
                    .props(Props.create(Cache.class)),
                "cache");

        cache.tell(new ConsistentHashableEnvelope(new Entry("hello", "HELLO"), "hello"), getRef());
        cache.tell(new ConsistentHashableEnvelope(new Entry("hi", "HI"), "hi"), getRef());

        cache.tell(new Get("hello"), getRef());
        expectMsgEquals("HELLO");

        cache.tell(new Get("hi"), getRef());
        expectMsgEquals("HI");

        cache.tell(new Evict("hi"), getRef());
        cache.tell(new Get("hi"), getRef());
        expectMsgEquals(NOT_FOUND);

        // #consistent-hashing-router
      }
    };
  }
}
