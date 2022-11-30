/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

// #metrics-listener
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.ClusterEvent.CurrentClusterState;
import org.apache.pekko.cluster.metrics.ClusterMetricsChanged;
import org.apache.pekko.cluster.metrics.NodeMetrics;
import org.apache.pekko.cluster.metrics.StandardMetrics;
import org.apache.pekko.cluster.metrics.StandardMetrics.HeapMemory;
import org.apache.pekko.cluster.metrics.StandardMetrics.Cpu;
import org.apache.pekko.cluster.metrics.ClusterMetricsExtension;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;

public class MetricsListener extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  Cluster cluster = Cluster.get(getContext().getSystem());

  ClusterMetricsExtension extension = ClusterMetricsExtension.get(getContext().getSystem());

  // Subscribe unto ClusterMetricsEvent events.
  @Override
  public void preStart() {
    extension.subscribe(getSelf());
  }

  // Unsubscribe from ClusterMetricsEvent events.
  @Override
  public void postStop() {
    extension.unsubscribe(getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            ClusterMetricsChanged.class,
            clusterMetrics -> {
              for (NodeMetrics nodeMetrics : clusterMetrics.getNodeMetrics()) {
                if (nodeMetrics.address().equals(cluster.selfAddress())) {
                  logHeap(nodeMetrics);
                  logCpu(nodeMetrics);
                }
              }
            })
        .match(
            CurrentClusterState.class,
            message -> {
              // Ignore.
            })
        .build();
  }

  void logHeap(NodeMetrics nodeMetrics) {
    HeapMemory heap = StandardMetrics.extractHeapMemory(nodeMetrics);
    if (heap != null) {
      log.info("Used heap: {} MB", ((double) heap.used()) / 1024 / 1024);
    }
  }

  void logCpu(NodeMetrics nodeMetrics) {
    Cpu cpu = StandardMetrics.extractCpu(nodeMetrics);
    if (cpu != null && cpu.systemLoadAverage().isDefined()) {
      log.info("Load: {} ({} processors)", cpu.systemLoadAverage().get(), cpu.processors());
    }
  }
}
// #metrics-listener
