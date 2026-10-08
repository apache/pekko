---
project.description: How to deploy an Apache Pekko Cluster to Kubernetes and Docker.
---
# Deploying

## Deploying to Kubernetes

Deploy to Kubernetes according to the guide and example project for [Deploying a Pekko Cluster to Kubernetes]($pekko.doc.dns$/docs/pekko-management/current/kubernetes-deployment/index.html), which requires more Kubernetes expertise.

### Cluster bootstrap

To take advantage of running inside Kubernetes while forming a cluster, 
[Pekko Cluster Bootstrap]($pekko.doc.dns$/docs/pekko-management/current/bootstrap/) helps forming or joining a cluster using Pekko Discovery with
the Kubernetes API or Kubernetes via DNS to discover peer nodes.

You can look at the
@java[[Cluster with Kubernetes example project](https://github.com/apache/pekko-samples/tree/main/pekko-sample-cluster-kubernetes-java)]
@scala[[Cluster with Kubernetes example project](https://github.com/apache/pekko-samples/tree/main/pekko-sample-cluster-kubernetes-scala)]
to see what this looks like in practice.
 
### Resource limits

To avoid CFS scheduler limits, it is best not to use `resources.limits.cpu` limits, but use `resources.requests.cpu` configuration instead.

## Deploying to Docker containers

You can use both Pekko remoting and Pekko Cluster inside Docker containers. Note
that you will need to take special care with the network configuration when using Docker,
described here: @ref:[Pekko behind NAT or in a Docker container](../remoting-artery.md#remote-configuration-nat-artery)

You can look at the
@java[[Cluster with docker-compose example project](https://github.com/apache/pekko-samples/tree/main/pekko-sample-cluster-docker-compose-java)]
@scala[[Cluster with docker-compose example project](https://github.com/apache/pekko-samples/tree/main/pekko-sample-cluster-docker-compose-scala)]
to see what this looks like in practice.

For the JVM to run well in a Docker container, there are some general (not Pekko specific) parameters that might need tuning:

### Resource constraints

Docker allows [constraining each containers' resource usage](https://docs.docker.com/config/containers/resource_constraints/).

#### Memory

Modern JVMs are container aware: container support (`-XX:+UseContainerSupport`) is enabled by default, so the JVM respects the c-group memory limits of the container.
By default the maximum heap size is only a fraction of the container memory limit, so you may want to tune it with `-XX:MaxRAMPercentage`.

#### CPU

For multi-threaded applications such as the JVM, the CFS scheduler limits are an ill fit, because they will restrict
the allowed CPU usage even when more CPU cycles are available from the host system. This means your application may be
starved of CPU time, but your system appears idle.

For this reason, it is best to avoid `--cpus` and `--cpu-quota` entirely, and instead specify relative container weights using `--cpu-shares` instead.

