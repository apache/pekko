/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.persistence.typed;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.persistence.typed.ReplicaId;
import org.apache.pekko.persistence.typed.ReplicationId;
import org.apache.pekko.persistence.typed.javadsl.*;

import java.util.*;

// #factory
public class MyReplicatedBehavior
    extends ReplicatedEventSourcedBehavior<
        MyReplicatedBehavior.Command, MyReplicatedBehavior.Event, MyReplicatedBehavior.State> {
  // #factory
  interface Command {}

  interface State {}

  interface Event {}

  // #replicas
  public static final ReplicaId DCA = new ReplicaId("DCA");
  public static final ReplicaId DCB = new ReplicaId("DCB");

  public static final Set<ReplicaId> ALL_REPLICAS =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(DCA, DCB)));
  // #replicas

  // #factory-shared
  public static Behavior<Command> create(
      String entityId, ReplicaId replicaId, String queryPluginId) {
    return ReplicatedEventSourcing.commonJournalConfig(
        new ReplicationId("MyReplicatedEntity", entityId, replicaId),
        ALL_REPLICAS,
        queryPluginId,
        MyReplicatedBehavior::new);
  }
  // #factory-shared

  // #factory
  public static Behavior<Command> create(String entityId, ReplicaId replicaId) {
    Map<ReplicaId, String> allReplicasAndQueryPlugins = new HashMap<>();
    allReplicasAndQueryPlugins.put(DCA, "journalForDCA");
    allReplicasAndQueryPlugins.put(DCB, "journalForDCB");

    return ReplicatedEventSourcing.perReplicaJournalConfig(
        new ReplicationId("MyReplicatedEntity", entityId, replicaId),
        allReplicasAndQueryPlugins,
        MyReplicatedBehavior::new);
  }

  private MyReplicatedBehavior(ReplicationContext replicationContext) {
    super(replicationContext);
  }
  // #factory

  @Override
  public State emptyState() {
    throw new UnsupportedOperationException("dummy for example");
  }

  @Override
  public CommandHandler<Command, Event, State> commandHandler() {
    throw new UnsupportedOperationException("dummy for example");
  }

  @Override
  public EventHandler<State, Event> eventHandler() {
    throw new UnsupportedOperationException("dummy for example");
  }
}
