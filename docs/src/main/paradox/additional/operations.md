---
project.description: Operating, managing and monitoring Pekko and Pekko Cluster applications.
---
# Operating a Cluster

This documentation discusses how to operate a cluster. For related, more specific guides
see @ref:[Packaging](packaging.md), @ref:[Deploying](deploying.md) and @ref:[Rolling Updates](rolling-updates.md).
 
## Starting 

### Cluster Bootstrap

When starting clusters on cloud systems such as Kubernetes, AWS, Google Cloud, Azure, Mesos or others,
you may want to automate the discovery of nodes for the cluster joining process, using your cloud providers,
cluster orchestrator, or other form of service discovery (such as managed DNS).

The open source Pekko Management library includes the @extref:[Cluster Bootstrap](pekko-management:bootstrap/index.html)
module which handles just that. Please refer to its documentation for more details.

@@@ note
 
If you are running Pekko in a Docker container or the nodes for some other reason have separate internal and
external ip addresses you must configure remoting according to @ref:[Pekko behind NAT or in a Docker container](../remoting-artery.md#remote-configuration-nat-artery)

@@@
 
## Stopping 

See @ref:[Rolling Updates, Cluster Shutdown and Coordinated Shutdown](../additional/rolling-updates.md#cluster-shutdown).

## Cluster Management

There are several management tools for the cluster. 
Complete information on running and managing Pekko applications can be found in 
the @exref:[Pekko Management](pekko-management:) project documentation.

<a id="cluster-http"></a>
### HTTP

Information and management of the cluster is available with a HTTP API.
See documentation of @extref:[Pekko Management](pekko-management:).

<a id="cluster-jmx"></a>
### JMX

Information and management of the cluster is available as JMX MBeans with the root name `org.apache.pekko.Cluster`.
The JMX information can be displayed with an ordinary JMX console such as JConsole or JVisualVM.

From JMX you can:

 * see what members that are part of the cluster
 * see status of this node
 * see roles of each member
 * join this node to another node in cluster
 * mark any node in the cluster as down
 * tell any node in the cluster to leave

Member nodes are identified by their address, in format *`pekko://actor-system-name@hostname:port`*.
