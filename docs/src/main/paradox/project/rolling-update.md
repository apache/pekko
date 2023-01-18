# Rolling Updates and Versions

## Pekko upgrades
Pekko supports rolling updates between two consecutive patch versions unless an exception is
mentioned on this page. For example updating from 2.5.15 to 2.5.16. Many times
it is also possible to skip several versions and exceptions to that are also described here.
For example it's possible to update from 2.5.14 to 2.5.16 without intermediate 2.5.15.

It's not supported to have a cluster with more than two different versions. Roll out the first
update completely before starting next update.

@@@ note

@ref:[Rolling update from classic remoting to Artery](../additional/rolling-updates.md#migrating-from-classic-remoting-to-artery) is not supported since the protocol
is completely different. It will require a full cluster shutdown and new startup.

@@@