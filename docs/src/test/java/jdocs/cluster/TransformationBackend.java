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

import static jdocs.cluster.TransformationMessages.BACKEND_REGISTRATION;

import jdocs.cluster.TransformationMessages.TransformationJob;
import jdocs.cluster.TransformationMessages.TransformationResult;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.ClusterEvent.CurrentClusterState;
import org.apache.pekko.cluster.ClusterEvent.MemberUp;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.MemberStatus;

// #backend
public class TransformationBackend extends AbstractActor {

  Cluster cluster = Cluster.get(getContext().getSystem());

  // subscribe to cluster changes, MemberUp
  @Override
  public void preStart() {
    cluster.subscribe(getSelf(), MemberUp.class);
  }

  // re-subscribe when restart
  @Override
  public void postStop() {
    cluster.unsubscribe(getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            TransformationJob.class,
            job -> {
              getSender().tell(new TransformationResult(job.getText().toUpperCase()), getSelf());
            })
        .match(
            CurrentClusterState.class,
            state -> {
              for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                  register(member);
                }
              }
            })
        .match(
            MemberUp.class,
            mUp -> {
              register(mUp.member());
            })
        .build();
  }

  void register(Member member) {
    if (member.hasRole("frontend"))
      getContext()
          .actorSelection(member.address() + "/user/frontend")
          .tell(BACKEND_REGISTRATION, getSelf());
  }
}
// #backend
