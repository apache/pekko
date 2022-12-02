/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.ClusterEvent.MemberEvent;
import org.apache.pekko.cluster.ClusterEvent.MemberUp;
import org.apache.pekko.cluster.ClusterEvent.MemberRemoved;
import org.apache.pekko.cluster.ClusterEvent.UnreachableMember;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;

public class SimpleClusterListener extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
  Cluster cluster = Cluster.get(getContext().getSystem());

  // subscribe to cluster changes
  @Override
  public void preStart() {
    // #subscribe
    cluster.subscribe(
        getSelf(), ClusterEvent.initialStateAsEvents(), MemberEvent.class, UnreachableMember.class);
    // #subscribe
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
            message -> {
              // ignore
            })
        .build();
  }
}
