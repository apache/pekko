# Release Notes (1.1.x)

## 1.1.0-M1

Release notes for Apache Pekko 1.1.0-M1. See [GitHub Milestone](https://github.com/apache/pekko/milestone/2?closed=1) for fuller list of changes.
As with all milestone releases, this release is not recommended for production use - it is designed to allow users to try out the changes in a test environment.

### 1.0.x changes

Apache Pekko 1.1.0-M1 contains all of the changes that have been released in the @ref:[1.0.x releases](releases-1.0.md) up to and including v1.0.3-M1.

### Bug Fixes

* in TlsActor, flush data to user only after handshake has finished ([PR1128](https://github.com/apache/pekko/pull/1128))
* proper path when promise actor terminated quickly ([PR1156](https://github.com/apache/pekko/pull/1156))
* TcpDnsClient cannot recover if registration on TcpConnection times out ([PR1183](https://github.com/apache/pekko/pull/1183))
* Fix uncaught decider exception in Split with Supervision.resumingDecider ([PR1207](https://github.com/apache/pekko/pull/1207))
* Pull instead of throw exception in groupBy operator ([PR1210](https://github.com/apache/pekko/pull/1210))
* Fix ByteIterator indexWhere method ([PR1282](https://github.com/apache/pekko/pull/1282))

### Additional APIs

* Add asInputStream to ByteString ([PR1085](https://github.com/apache/pekko/pull/1085))
* Add new indexOf functions to ByteString for byte lookups ([PR1247](https://github.com/apache/pekko/pull/1247))
* Add create method to PFBuilder ([PR947](https://github.com/apache/pekko/pull/947))
* Add missing create method to javadsl Graph ([PR1230](https://github.com/apache/pekko/pull/1230))

The Stream API has been updated to add some extra functions. 

* add collectFirst stream operator ([PR984](https://github.com/apache/pekko/pull/984))
* add collectWhile stream operator ([PR964](https://github.com/apache/pekko/pull/964))
* add dimap stream operator ([PR942](https://github.com/apache/pekko/pull/942))
* add flatten stream operator ([PR937](https://github.com/apache/pekko/pull/937))
* add flattenMerge stream operator ([PR1045](https://github.com/apache/pekko/pull/1045))
* add foldWhile stream operator ([PR1012](https://github.com/apache/pekko/pull/1012))
* add mapAsyncPartitioned / mapAsyncPartitionedUnordered stream operators ([PR561](https://github.com/apache/pekko/pull/561), [PR676](https://github.com/apache/pekko/pull/676))
* add mapWithResource stream operator ([PR931](https://github.com/apache/pekko/pull/931), [PR1053](https://github.com/apache/pekko/pull/1053))
* add onErrorComplete stream operator ([PR913](https://github.com/apache/pekko/pull/913))
* add support for `for` comprehensions ([PR935](https://github.com/apache/pekko/pull/935))
* add Sink.exists operator ([PR990](https://github.com/apache/pekko/pull/990))
* add Sink.forall operator ([PR989](https://github.com/apache/pekko/pull/989))
* add Source.iterate operator ([PR1244](https://github.com/apache/pekko/pull/1244))
* added extra retry operators that allow users to provide a predicate to decide whether to retry based on the exception ([PR1269](https://github.com/apache/pekko/pull/1269))

The Stream Testkit Java DSL has some extra functions.

* Add more Java DSL functions to StreamTestKit to better match the Scala DSL ([PR1186](https://github.com/apache/pekko/pull/1186))
* Add expectNextN to StreamTestKit for javadsl ([PR962](https://github.com/apache/pekko/pull/962))

### Removed

* The pekko-protobuf jar is no longer published. The pekko-protobuf-v3 jar is still published ([PR489](https://github.com/apache/pekko/pull/489))

### Other Changes

* Scala 2 inline optimizer has been enabled
* Support `fork-join-executor.maximum-pool-size` config ([PR485](https://github.com/apache/pekko/pull/485))
* Classic Remoting was updated to use Netty 4 instead of Netty 3 ([PR643](https://github.com/apache/pekko/pull/643))
* Replace SubstreamCancelStrategy with SupervisionDecider for Split ([PR252](https://github.com/apache/pekko/pull/252))
* Support Jackson StreamReadConstraints and StreamWriteConstraints ([PR564](https://github.com/apache/pekko/pull/564))
* Support configuration for Jackson Recycler Pool ([PR1192](https://github.com/apache/pekko/pull/1192))
* pekko-multi-node-testkit was changed to use Netty 4 instead of Netty 3 ([PR539](https://github.com/apache/pekko/pull/539))
* add junit5 support to pekko-testkit-typed ([PR751](https://github.com/apache/pekko/pull/751))
* Fix maybe throw for MinimalStage (Stream Unfold). ([PR822](https://github.com/apache/pekko/pull/822))
* Add dedicated stream timeout exceptions for timeout related operators ([PR861](https://github.com/apache/pekko/pull/861))
* Reimplement MapConcat operator without statefulMapConcat. ([PR902](https://github.com/apache/pekko/pull/902))
* Make SingleConsumerMultiProducer the default mailbox for stream. ([PR917](https://github.com/apache/pekko/pull/917))
* Rework PhiAccrualFailureDetector to enable monitoring of interval. ([PR1137](https://github.com/apache/pekko/pull/1137))
* Remove the deprecation of statefulMapConcat operator. ([PR1147](https://github.com/apache/pekko/pull/1147))
* Add AbruptStreamTerminationException as super class of some related exceptions. ([PR1201](https://github.com/apache/pekko/pull/1201))
* For Pekko Persistence DurableState API, a new DeleteRevisionException has been added and the aim is to have implementations fail with that exception if a deleteObject does not delete exactly one record for that revision. ([PR1271](https://github.com/apache/pekko/pull/1271))
* Some performance changes in the Stream code ([PR48](https://github.com/apache/pekko/pull/48), [PR49](https://github.com/apache/pekko/pull/49), [PR278](https://github.com/apache/pekko/pull/278), [PR363](https://github.com/apache/pekko/pull/363), [PR408](https://github.com/apache/pekko/pull/408), [PR872](https://github.com/apache/pekko/pull/872), [PR923](https://github.com/apache/pekko/pull/923), [PR983](https://github.com/apache/pekko/pull/983), [PR1001](https://github.com/apache/pekko/pull/1001), [PR1027](https://github.com/apache/pekko/pull/1027), [PR1249](https://github.com/apache/pekko/pull/1249), [PR1250](https://github.com/apache/pekko/pull/1250))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* The protobuf-java code that is shaded and released as pekko-protobuf-v3 has been upgraded to protobuf-java 3.25.3
* slf4j was updated to v2 ([PR748](https://github.com/apache/pekko/pull/748))
* upgrade from netty 3 to netty 4 (pekko-remote and pekko-multi-node-testkit)
* Jackson 2.17.1

### Known Issues

* For Scala 2.12 users, we have run into an issue with stream-testkit function `expectNextWithTimeoutPF` ([#1393](https://github.com/apache/pekko/issues/1393)).
    * For now, the consensus is not to change this as it appears to be more of a Scala 2.12 compiler issue.
    * Affected Scala 2.12 users can stick with Pekko 1.0 or change their code to get it to compile. The most reliable code change is to move the PartialFunction code and declare it as a `val`.
    * If you feel strongly that Apache Pekko should try to fix this, please get in touch. 
