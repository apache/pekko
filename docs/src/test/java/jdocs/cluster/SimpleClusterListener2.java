/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.ClusterEvent.CurrentClusterState;
import org.apache.pekko.cluster.ClusterEvent.MemberEvent;
import org.apache.pekko.cluster.ClusterEvent.MemberUp;
import org.apache.pekko.cluster.ClusterEvent.MemberRemoved;
import org.apache.pekko.cluster.ClusterEvent.UnreachableMember;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;

public class SimpleClusterListener2 extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
  // #join
  Cluster cluster = Cluster.get(getContext().getSystem());
  // #join

  // subscribe to cluster changes
  @Override
  public void preStart() {
    // #join
    cluster.join(cluster.selfAddress());
    // #join

    // #subscribe
    cluster.subscribe(getSelf(), MemberEvent.class, UnreachableMember.class);
    // #subscribe

    // #register-on-memberup
    cluster.registerOnMemberUp(
        () -> cluster.subscribe(getSelf(), MemberEvent.class, UnreachableMember.class));
    // #register-on-memberup
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
            CurrentClusterState.class,
            state -> {
              log.info("Current members: {}", state.members());
            })
        .match(
            MemberUp.class,
            mUp -> {
              log.info("Member is Up: {}", mUp.member());
            })
        .match(
            UnreachableMember.class,
            mUnreachable -> {
              log.info("Member detected as unreachable: {}", mUnreachable.member());
            })
        .match(
            MemberRemoved.class,
            mRemoved -> {
              log.info("Member is Removed: {}", mRemoved.member());
            })
        .match(
            MemberEvent.class,
            event -> {
              // ignore
            })
        .build();
  }
}
