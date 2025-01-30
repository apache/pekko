/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed;

import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Join;
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin;
import org.apache.pekko.persistence.testkit.query.javadsl.PersistenceTestKitReadJournal;
import org.apache.pekko.persistence.typed.ReplicaId;
import org.apache.pekko.persistence.typed.ReplicationId;
import org.apache.pekko.persistence.typed.javadsl.*;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.apache.pekko.cluster.sharding.typed.ReplicatedShardingTest.ProxyActor.ALL_REPLICAS;
import static org.junit.Assert.assertEquals;

public class ReplicatedShardingTest extends JUnitSuite {

  static class MyReplicatedStringSet
      extends ReplicatedEventSourcedBehavior<MyReplicatedStringSet.Command, String, Set<String>> {
    interface Command {}

    static class Add implements Command {
      public final String text;

      public Add(String text) {
        this.text = text;
      }
    }

    static class GetTexts implements Command {
      public final ActorRef<Texts> replyTo;

      public GetTexts(ActorRef<Texts> replyTo) {
        this.replyTo = replyTo;
      }
    }

    static class Texts {
      public final Set<String> texts;

      public Texts(Set<String> texts) {
        this.texts = texts;
      }
    }

    static Behavior<Command> create(ReplicationId replicationId) {
      return ReplicatedEventSourcing.commonJournalConfig(
          replicationId,
          ALL_REPLICAS,
          PersistenceTestKitReadJournal.Identifier(),
          MyReplicatedStringSet::new);
    }

    private MyReplicatedStringSet(ReplicationContext replicationContext) {
      super(replicationContext);
    }

    @Override
    public Set<String> emptyState() {
      return new HashSet<>();
    }

    @Override
    public CommandHandler<Command, String, Set<String>> commandHandler() {
      return newCommandHandlerBuilder()
          .forAnyState()
          .onCommand(Add.class, add -> Effect().persist(add.text))
          .onCommand(
              GetTexts.class,
              (state, get) -> {
                // protective copy
                get.replyTo.tell(new Texts(new HashSet<>(state)));
                return Effect().none();
              })
          .build();
    }

    @Override
    public EventHandler<Set<String>, String> eventHandler() {
      return newEventHandlerBuilder()
          .forAnyState()
          .onAnyEvent(
              (state, text) -> {
                state.add(text);
                return state;
              });
    }
  }

  public static class ProxyActor extends AbstractBehavior<ProxyActor.Command> {
    interface Command {}

    public static final class ForwardToRandom implements Command {
      public final String entityId;
      public final MyReplicatedStringSet.Command message;

      public ForwardToRandom(String entityId, MyReplicatedStringSet.Command message) {
        this.entityId = entityId;
        this.message = message;
      }
    }

    public static final class ForwardToAll implements Command {
      public final String entityId;
      public final MyReplicatedStringSet.Command message;

      public ForwardToAll(String entityId, MyReplicatedStringSet.Command message) {
        this.entityId = entityId;
        this.message = message;
      }
    }

    public static Behavior<Command> create() {
      return Behaviors.setup(ProxyActor::new);
    }

    public static final Set<ReplicaId> ALL_REPLICAS =
        Collections.unmodifiableSet(
            new HashSet<>(
                Arrays.asList(
                    new ReplicaId("DC-A"), new ReplicaId("DC-B"), new ReplicaId("DC-C"))));

    private final ReplicatedSharding<MyReplicatedStringSet.Command> replicatedSharding;

    private ProxyActor(ActorContext<Command> context) {
      super(context);

      // #bootstrap
      ReplicatedEntityProvider<MyReplicatedStringSet.Command> replicatedEntityProvider =
          ReplicatedEntityProvider.create(
              MyReplicatedStringSet.Command.class,
              "StringSet",
              ALL_REPLICAS,
              // factory for replicated entity for a given replica
              (entityTypeKey, replicaId) ->
                  ReplicatedEntity.create(
                      replicaId,
                      // use the replica id as typekey for sharding to get one sharding instance
                      // per replica
                      Entity.of(
                              entityTypeKey,
                              entityContext ->
                                  // factory for the entity for a given entity in that replica
                                  MyReplicatedStringSet.create(
                                      ReplicationId.fromString(entityContext.getEntityId())))
                          // potentially use replica id as role or dc in Pekko multi dc for the
                          // sharding instance
                          // to control where replicas will live
                          // .withDataCenter(replicaId.id()))
                          .withRole(replicaId.id())));

      ReplicatedShardingExtension extension =
          ReplicatedShardingExtension.get(getContext().getSystem());
      ReplicatedSharding<MyReplicatedStringSet.Command> replicatedSharding =
          extension.init(replicatedEntityProvider);
      // #bootstrap

      this.replicatedSharding = replicatedSharding;
    }

    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
          .onMessage(ForwardToRandom.class, this::onForwardToRandom)
          .onMessage(ForwardToAll.class, this::onForwardToAll)
          .build();
    }

    private Behavior<Command> onForwardToRandom(ForwardToRandom forwardToRandom) {
      Map<ReplicaId, EntityRef<MyReplicatedStringSet.Command>> refs =
          replicatedSharding.getEntityRefsFor(forwardToRandom.entityId);
      int chosenIdx = RandomNumberGenerator.get().nextInt(refs.size());
      new ArrayList<>(refs.values()).get(chosenIdx).tell(forwardToRandom.message);
      return this;
    }

    private Behavior<Command> onForwardToAll(ForwardToAll forwardToAll) {
      // #all-entity-refs
      Map<ReplicaId, EntityRef<MyReplicatedStringSet.Command>> refs =
          replicatedSharding.getEntityRefsFor(forwardToAll.entityId);
      refs.forEach((replicaId, ref) -> ref.tell(forwardToAll.message));
      // #all-entity-refs
      return this;
    }
  }

  @ClassRule
  public static final TestKitJunitResource testKit =
      new TestKitJunitResource(
          ConfigFactory.parseString(
                  " pekko.loglevel = DEBUG\n"
                      + "      pekko.loggers = [\"org.apache.pekko.testkit.SilenceAllTestEventListener\"]\n"
                      + "      pekko.actor.provider = \"cluster\"\n"
                      + "      # pretend we're a node in all dc:s\n"
                      + "      pekko.cluster.roles = [\"DC-A\", \"DC-B\", \"DC-C\"]\n"
                      + "      pekko.remote.classic.netty.tcp.port = 0\n"
                      + "      pekko.remote.artery.canonical.port = 0")
              .withFallback(PersistenceTestKitPlugin.getInstance().config()));

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  @Test
  public void formClusterAndInteractWithReplicas() {
    // join ourselves to form a one node cluster
    Cluster node = Cluster.get(testKit.system());
    node.manager().tell(new Join(node.selfMember().address()));
    TestProbe<Object> testProbe = testKit.createTestProbe();
    testProbe.awaitAssert(
        () -> {
          assertEquals(MemberStatus.up(), node.selfMember().status());
          return null;
        });

    // forward messages to replicas
    ActorRef<ProxyActor.Command> proxy = testKit.spawn(ProxyActor.create());

    proxy.tell(new ProxyActor.ForwardToAll("id1", new MyReplicatedStringSet.Add("to-all")));
    proxy.tell(new ProxyActor.ForwardToRandom("id1", new MyReplicatedStringSet.Add("to-random")));

    testProbe.awaitAssert(
        () -> {
          TestProbe<MyReplicatedStringSet.Texts> responseProbe = testKit.createTestProbe();
          proxy.tell(
              new ProxyActor.ForwardToAll(
                  "id1", new MyReplicatedStringSet.GetTexts(responseProbe.ref())));
          List<MyReplicatedStringSet.Texts> responses = responseProbe.receiveSeveralMessages(3);
          Set<String> uniqueTexts =
              responses.stream().flatMap(res -> res.texts.stream()).collect(Collectors.toSet());
          assertEquals(new HashSet<>(Arrays.asList("to-all", "to-random")), uniqueTexts);
          return null;
        });
  }
}
