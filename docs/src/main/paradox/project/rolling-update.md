# Rolling Updates and Versions

## Apache Pekko Upgrades
Pekko supports rolling updates between two consecutive patch versions unless an exception is
mentioned on this page. For example updating from 1.0.0 to 1.0.1. Many times,
it is also possible to skip several versions and exceptions to that are also described here.

It's not supported to have a cluster with more than two different versions. Roll out the first
update completely before starting next update.

@@@ note

@ref:[Rolling update from classic remoting to Artery](../additional/rolling-updates.md#migrating-from-classic-remoting-to-artery) is not supported since the protocol
is completely different. It will require a full cluster shutdown and new startup.

@@@

## Upgrading from Pekko 1.x to 2.x

Pekko 2.x is not binary compatible with Pekko 1.x, so all the Pekko modules (and any libraries built on
Pekko 1.x) must be upgraded together. Read the
@ref:[migration guide](../migration/migration-guide-1.x-2.x.md) before planning a rolling update, in
particular the breaking changes and the configuration changes, and check how they affect the messages
exchanged between nodes, your persisted data and your configuration.

For a rolling update of a cluster from Pekko 1.x to 2.x:

* First upgrade the whole cluster to the latest Pekko 1.7.x release. Pekko 2.x sends a different Artery
  TCP magic header by default, which Pekko 1.6.x and earlier reject, see
  @ref:[Changing TCP magic header](../additional/rolling-updates.md#changing-tcp-magic-header).
* Then roll out Pekko 2.x as a separate update.
