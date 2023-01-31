---
project.description: How to do rolling updates and restarts with Apache Pekko Cluster.
---
# Rolling Updates

@@@ note

There are a few instances @ref:[when a full cluster restart is required](#when-shutdown-startup-is-required)
versus being able to do a rolling update.

@@@

A rolling update is the process of replacing one version of the system with another without downtime.
The changes can be new code, changed dependencies such as new Pekko version, or modified configuration.

In Apache Pekko, rolling updates are typically used for a stateful Pekko Cluster where you can't run two separate clusters in
parallel during the update, for example in blue green deployments.

For rolling updates related to Pekko dependency version upgrades and the migration guides, please see
@ref:[Rolling Updates and Pekko versions](../project/rolling-update.md)

## Serialization Compatibility

There are two parts of Pekko that need careful consideration when performing an rolling update.

1. Compatibility of remote message protocols. Old nodes may send messages to new nodes and vice versa.
1. Serialization format of persisted events and snapshots. New nodes must be able to read old data, and
   during the update old nodes must be able to read data stored by new nodes.

There are many more application specific aspects for serialization changes during rolling updates to consider. 
For example based on the use case and requirements, whether to allow dropped messages or tear down the TCP connection when the manifest is unknown.
When some message loss during a rolling update is acceptable versus a full shutdown and restart, assuming the application recovers afterwards 
* If a `java.io.NotSerializableException` is thrown in `fromBinary` this is treated as a transient problem, the issue logged and the message is dropped
* If other exceptions are thrown it can be an indication of corrupt bytes from the underlying transport, and the connection is broken

For more zero-impact rolling updates, it is important to consider a strategy for serialization that can be evolved. 
One approach to retiring a serializer without downtime is described in @ref:[two rolling update steps to switch to the new serializer](../serialization.md#rolling-updates). 
Additionally you can find advice on @ref:[Persistence - Schema Evolution](../persistence-schema-evolution.md) which also applies to remote messages when deploying with rolling updates.

## Cluster Sharding

During a rolling update, sharded entities receiving traffic may be moved, based on the pluggable allocation
strategy and settings. When an old node is stopped the shards that were running on it are moved to one of the
other remaining nodes in the cluster when messages are sent to those shards.

To make rolling updates as smooth as possible there is a configuration property that defines the version of the
application. This is used by rolling update features to distinguish between old and new nodes. For example,
the default `LeastShardAllocationStrategy` avoids allocating shards to old nodes during a rolling update.
The `LeastShardAllocationStrategy` sees that there is rolling update in progress when there are members with
different configured `app-version`.

To make use of this feature you need to define the `app-version` and increase it for each rolling update.

```
pekko.cluster.app-version = 1.2.3
```

To understand which is old and new it compares the version numbers using normal conventions,
see @apidoc[util.Version] for more details.

Rebalance is also disabled during rolling updates, since shards from stopped nodes are anyway supposed to be
started on new nodes. Messages to shards that were stopped on the old nodes will allocate corresponding shards
on the new nodes, without waiting for rebalance actions. 

You should also enable the @ref:[health check for Cluster Sharding](../typed/cluster-sharding.md#health-check) if
you use Pekko Management. The readiness check will delay incoming traffic to the node until Sharding has been
initialized and can accept messages.

The `ShardCoordinator` is itself a cluster singleton.
To minimize downtime of the shard coordinator, see the strategies about @ref[ClusterSingleton](#cluster-singleton) rolling updates below.

A few specific changes to sharding configuration require @ref:[a full cluster restart](#cluster-sharding-configuration-change).

## Cluster Singleton

Cluster singletons are always running on the oldest node. To avoid moving cluster singletons more than necessary during a rolling update, 
it is recommended to upgrade the oldest node last. This way cluster singletons are only moved once during a full rolling update. 
Otherwise, in the worst case cluster singletons may be migrated from node to node which requires coordination and initialization 
overhead several times.

[Kubernetes Deployments](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/) with `RollingUpdate`
strategy will roll out updates in this preferred order, from newest to oldest. 

## Cluster Shutdown
 
### Graceful shutdown 

For rolling updates it is best to leave the Cluster gracefully via @ref:[Coordinated Shutdown](../coordinated-shutdown.md),
which will run automatically on SIGTERM, when the Cluster node sees itself as `Exiting`.
Environments such as Kubernetes send a SIGTERM, however if the JVM is wrapped with a script ensure that it forwards the signal.
@ref:[Graceful shutdown](../cluster-sharding.md#graceful-shutdown) of Cluster Singletons and Cluster Sharding similarly happen automatically.

### Ungraceful shutdown 

In case of network failures it may still be necessary to set the node's status to Down in order to complete the removal. 
@ref:[Cluster Downing](../typed/cluster.md#downing) details downing nodes and downing providers. 
@ref:[Split Brain Resolver](../split-brain-resolver.md) can be used to ensure 
the cluster continues to function during network partitions and node failures. For example
if there is an unreachability problem Split Brain Resolver would make a decision based on the configured downing strategy. 
  
## Configuration Compatibility Checks

During rolling updates the configuration from existing nodes should pass the Cluster configuration compatibility checks.
For example, it is possible to migrate Cluster Sharding from Classic to Typed Actors in a rolling update using a two step approach:

* Deploy with the new nodes set to `pekko.cluster.configuration-compatibility-check.enforce-on-join = off`
and ensure all nodes are in this state
* Deploy again and with the new nodes set to `pekko.cluster.configuration-compatibility-check.enforce-on-join = on`. 
  
Full documentation about enforcing these checks on joining nodes and optionally adding custom checks can be found in  
@ref:[Pekko Cluster configuration compatibility checks](../typed/cluster.md#configuration-compatibility-check).

## When Shutdown Startup Is Required
 
There are a few instances when a full shutdown and startup is required versus being able to do a rolling update.

### Cluster Sharding configuration change

If you need to change any of the following aspects of sharding it will require a full cluster restart versus a rolling update:

 * The `extractShardId` function
 * The role that the shard regions run on
 * The persistence mode - It's important to use the same mode on all nodes in the cluster
 * The @ref:[`number-of-shards`](../typed/cluster-sharding.md#basic-example) - Note: changing the number
 of nodes in the cluster does not require changing the number of shards.
 
### Cluster configuration change

* A full restart is required if you change the [SBR strategy]($pekko.doc.dns$/docs/pekko/current/split-brain-resolver.html#strategies)

### Migrating from PersistentFSM to EventSourcedBehavior

If you've @ref:[migrated from `PersistentFSM` to `EventSourcedBehavior`](../persistence-fsm.md#migration-to-eventsourcedbehavior)
and are using PersistenceFSM with Cluster Sharding, a full shutdown is required as shards can move between new and old nodes.
  
### Migrating from classic remoting to Artery

If you've migrated from classic remoting to Artery
which has a completely different protocol, a rolling update is not supported.

### Changing remoting transport

Rolling update is not supported when @ref:[changing the remoting transport](../remoting-artery.md#selecting-a-transport).

### Migrating from Classic Sharding to Typed Sharding

If you have been using classic sharding it is possible to do a rolling update to typed sharding using a 3 step procedure.
The steps along with example commits are detailed in [this sample PR](https://github.com/akka/akka-samples/pull/110) 
