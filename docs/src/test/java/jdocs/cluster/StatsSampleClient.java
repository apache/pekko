/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdocs.cluster.StatsMessages.JobFailed;
import jdocs.cluster.StatsMessages.StatsJob;
import jdocs.cluster.StatsMessages.StatsResult;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;
import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.actor.Address;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.ClusterEvent.UnreachableMember;
import org.apache.pekko.cluster.ClusterEvent.ReachableMember;
import org.apache.pekko.cluster.ClusterEvent.CurrentClusterState;
import org.apache.pekko.cluster.ClusterEvent.MemberEvent;
import org.apache.pekko.cluster.ClusterEvent.MemberUp;
import org.apache.pekko.cluster.ClusterEvent.ReachabilityEvent;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.MemberStatus;

public class StatsSampleClient extends AbstractActor {

  final String servicePath;
  final Cancellable tickTask;
  final Set<Address> nodes = new HashSet<Address>();

  Cluster cluster = Cluster.get(getContext().getSystem());

  public StatsSampleClient(String servicePath) {
    this.servicePath = servicePath;
    Duration interval = Duration.ofMillis(2);
    tickTask =
        getContext()
            .getSystem()
            .scheduler()
            .scheduleWithFixedDelay(
                interval, interval, getSelf(), "tick", getContext().getDispatcher(), null);
  }

  // subscribe to cluster changes, MemberEvent
  @Override
  public void preStart() {
    cluster.subscribe(getSelf(), MemberEvent.class, ReachabilityEvent.class);
  }

  // re-subscribe when restart
  @Override
  public void postStop() {
    cluster.unsubscribe(getSelf());
    tickTask.cancel();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals(
            "tick",
            x -> !nodes.isEmpty(),
            x -> {
              // just pick any one
              List<Address> nodesList = new ArrayList<Address>(nodes);
              Address address =
                  nodesList.get(ThreadLocalRandom.current().nextInt(nodesList.size()));
              ActorSelection service = getContext().actorSelection(address + servicePath);
              service.tell(new StatsJob("this is the text that will be analyzed"), getSelf());
            })
        .match(StatsResult.class, System.out::println)
        .match(JobFailed.class, System.out::println)
        .match(
            CurrentClusterState.class,
            state -> {
              nodes.clear();
              for (Member member : state.getMembers()) {
                if (member.hasRole("compute") && member.status().equals(MemberStatus.up())) {
                  nodes.add(member.address());
                }
              }
            })
        .match(
            MemberUp.class,
            mUp -> {
              if (mUp.member().hasRole("compute")) nodes.add(mUp.member().address());
            })
        .match(
            MemberEvent.class,
            event -> {
              nodes.remove(event.member().address());
            })
        .match(
            UnreachableMember.class,
            unreachable -> {
              nodes.remove(unreachable.member().address());
            })
        .match(
            ReachableMember.class,
            reachable -> {
              if (reachable.member().hasRole("compute")) nodes.add(reachable.member().address());
            })
        .build();
  }
}
