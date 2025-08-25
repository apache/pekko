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

package jdocs.sharding;

import java.util.Optional;
import java.time.Duration;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorInitializationException;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.OneForOneStrategy;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.SupervisorStrategy;
import org.apache.pekko.actor.ReceiveTimeout;
// #counter-extractor
import org.apache.pekko.cluster.sharding.ShardRegion;

// #counter-extractor

// #counter-start
import org.apache.pekko.cluster.sharding.ClusterSharding;
import org.apache.pekko.cluster.sharding.ClusterShardingSettings;

// #counter-start
import org.apache.pekko.persistence.AbstractPersistentActor;
import org.apache.pekko.japi.pf.DeciderBuilder;

// Doc code, compile only
public class ClusterShardingTest {

  ActorSystem system = null;

  ActorRef getSelf() {
    return null;
  }

  public void demonstrateUsage() {
    // #counter-extractor
    ShardRegion.MessageExtractor messageExtractor =
        new ShardRegion.MessageExtractor() {

          @Override
          public String entityId(Object message) {
            if (message instanceof Counter.EntityEnvelope envelope)
              return String.valueOf(envelope.id);
            else if (message instanceof Counter.Get get)
              return String.valueOf(get.counterId);
            else return null;
          }

          @Override
          public Object entityMessage(Object message) {
            if (message instanceof Counter.EntityEnvelope envelope)
              return envelope.payload;
            else return message;
          }

          @Override
          public String shardId(Object message) {
            int numberOfShards = 100;
            if (message instanceof Counter.EntityEnvelope envelope) {
              long id = envelope.id;
              return String.valueOf(id % numberOfShards);
            } else if (message instanceof Counter.Get get) {
              long id = get.counterId;
              return String.valueOf(id % numberOfShards);
            } else {
              return null;
            }
          }
        };
    // #counter-extractor

    // #counter-start
    Optional<String> roleOption = Optional.empty();
    ClusterShardingSettings settings = ClusterShardingSettings.create(system);
    ActorRef startedCounterRegion =
        ClusterSharding.get(system)
            .start("Counter", Props.create(Counter.class), settings, messageExtractor);
    // #counter-start

    // #counter-usage
    ActorRef counterRegion = ClusterSharding.get(system).shardRegion("Counter");
    counterRegion.tell(new Counter.Get(123), getSelf());

    counterRegion.tell(new Counter.EntityEnvelope(123, Counter.CounterOp.INCREMENT), getSelf());
    counterRegion.tell(new Counter.Get(123), getSelf());
    // #counter-usage

    // #counter-supervisor-start
    ClusterSharding.get(system)
        .start(
            "SupervisedCounter", Props.create(CounterSupervisor.class), settings, messageExtractor);
    // #counter-supervisor-start

    // #proxy-dc
    ActorRef counterProxyDcB =
        ClusterSharding.get(system)
            .startProxy(
                "Counter",
                Optional.empty(),
                Optional.of("B"), // data center name
                messageExtractor);
    // #proxy-dc
  }

  public void demonstrateUsage2() {
    ShardRegion.MessageExtractor messageExtractor =
        new ShardRegion.MessageExtractor() {

          @Override
          public String entityId(Object message) {
            if (message instanceof Counter.EntityEnvelope envelope)
              return String.valueOf(envelope.id);
            else if (message instanceof Counter.Get get)
              return String.valueOf(get.counterId);
            else return null;
          }

          @Override
          public Object entityMessage(Object message) {
            if (message instanceof Counter.EntityEnvelope envelope)
              return envelope.payload;
            else return message;
          }

          // #extractShardId-StartEntity
          @Override
          public String shardId(Object message) {
            int numberOfShards = 100;
            if (message instanceof Counter.EntityEnvelope envelope) {
              long id = envelope.id;
              return String.valueOf(id % numberOfShards);
            } else if (message instanceof Counter.Get get) {
              long id = get.counterId;
              return String.valueOf(id % numberOfShards);
            } else if (message instanceof ShardRegion.StartEntity entity) {
              long id = Long.valueOf(entity.entityId());
              return String.valueOf(id % numberOfShards);
            } else {
              return null;
            }
          }
          // #extractShardId-StartEntity

        };
  }

  public // #counter-actor
  static class Counter extends AbstractPersistentActor {

    public enum CounterOp {
      INCREMENT,
      DECREMENT
    }

    public static class Get {
      public final long counterId;

      public Get(long counterId) {
        this.counterId = counterId;
      }
    }

    public static class EntityEnvelope {
      public final long id;
      public final Object payload;

      public EntityEnvelope(long id, Object payload) {
        this.id = id;
        this.payload = payload;
      }
    }

    public static class CounterChanged {
      public final int delta;

      public CounterChanged(int delta) {
        this.delta = delta;
      }
    }

    int count = 0;

    // getSelf().path().name() is the entity identifier (utf-8 URL-encoded)
    @Override
    public String persistenceId() {
      return "Counter-" + getSelf().path().name();
    }

    @Override
    public void preStart() throws Exception {
      super.preStart();
      getContext().setReceiveTimeout(Duration.ofSeconds(120));
    }

    void updateState(CounterChanged event) {
      count += event.delta;
    }

    @Override
    public Receive createReceiveRecover() {
      return receiveBuilder().match(CounterChanged.class, this::updateState).build();
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(Get.class, this::receiveGet)
          .matchEquals(CounterOp.INCREMENT, msg -> receiveIncrement())
          .matchEquals(CounterOp.DECREMENT, msg -> receiveDecrement())
          .matchEquals(ReceiveTimeout.getInstance(), msg -> passivate())
          .build();
    }

    private void receiveGet(Get msg) {
      getSender().tell(count, getSelf());
    }

    private void receiveIncrement() {
      persist(new CounterChanged(+1), this::updateState);
    }

    private void receiveDecrement() {
      persist(new CounterChanged(-1), this::updateState);
    }

    private void passivate() {
      getContext().getParent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
    }
  }

  // #counter-actor

  public // #supervisor
  static class CounterSupervisor extends AbstractActor {

    private final ActorRef counter =
        getContext().actorOf(Props.create(Counter.class), "theCounter");

    private static final SupervisorStrategy strategy =
        new OneForOneStrategy(
            DeciderBuilder.match(IllegalArgumentException.class, e -> SupervisorStrategy.resume())
                .match(ActorInitializationException.class, e -> SupervisorStrategy.stop())
                .match(Exception.class, e -> SupervisorStrategy.restart())
                .matchAny(o -> SupervisorStrategy.escalate())
                .build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
      return strategy;
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(Object.class, msg -> counter.forward(msg, getContext()))
          .build();
    }
  }
  // #supervisor

}
