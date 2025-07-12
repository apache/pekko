# Release Notes (1.2.x)

Apache Pekko 1.2.x releases support Java 8 and above.

# 1.2.0-M2

Pekko 1.2.0-M2 has some new features, performance updates and dependency upgrades. See the [GitHub Milestone](https://github.com/apache/pekko/milestone/15?closed=1) for a fuller list of changes.

This is milestone release and is aimed at testing some new changes. This release should ideally not be used in production.

### Bug Fixes

* Issue forming mixed Akka/Pekko cluster when classic remoting with SSL/TLS is used ([PR1857](https://github.com/apache/pekko/pull/1857))
* Join cluster check adjusted to support Akka nodes ([PR1866](https://github.com/apache/pekko/pull/1866), [PR1877](https://github.com/apache/pekko/pull/1877))
    * If you are attempting to mix Akka and Pekko nodes in a cluster, it is still recommended to disable the join cluster check but these changes may be enough to get it work ([docs](https://cwiki.apache.org/confluence/display/PEKKO/Pekko+Akka+Compatibility)).
* BroadcastHub drops elements due to register/unregister race ([PR1841](https://github.com/apache/pekko/pull/1841))
* Fix issue with number deserialization in pekko-cluster-metrics ([PR1899](https://github.com/apache/pekko/pull/1899))
* Fix typed persistence stack overflow with many read only commands ([PR1919](https://github.com/apache/pekko/pull/1919))


### Additions

* Add Pattern timeout support ([PR1424](https://github.com/apache/pekko/pull/1424))
* Add TraversalBuilder.getValuePresentedSource method for further optimization ([PR1701](https://github.com/apache/pekko/pull/1701))
* Add flatmapConcat with parallelism support ([PR1702](https://github.com/apache/pekko/pull/1702))
* add EventsByTagQuery to JavaDSL PersistenceTestKitReadJournal ([PR1763](https://github.com/apache/pekko/pull/1763))
* Implement EventsBySliceQuery in JavaDSL PersistenceTestKitReadJournal ([PR1767](https://github.com/apache/pekko/pull/1767))
* Persistence API: add CurrentLastSequenceNumberByPersistenceIdQuery ([PR1773](https://github.com/apache/pekko/pull/1773))
* Add emitMulti with Spliterator support ([PR1776](https://github.com/apache/pekko/pull/1776))
* Add switchMap stream operator ([PR1787](https://github.com/apache/pekko/pull/1787))
* Add invokeWithFeedbackCompletionStage for javadsl ([PR1819](https://github.com/apache/pekko/pull/1819))
* Add takeUntil stream operator ([PR1820](https://github.com/apache/pekko/pull/1820))
* Add Source#create method ([PR1823](https://github.com/apache/pekko/pull/1823))
* Support Jackson Enum Features ([PR1845](https://github.com/apache/pekko/pull/1845))
* Add dropRepeated stream operator ([PR1868](https://github.com/apache/pekko/pull/1868))
* Add onComplete support for statefulMapConcat operator ([PR1870](https://github.com/apache/pekko/pull/1870))
* Add groupedAdjacentBy and GroupedAdjacentByWeighted stream operators ([PR1937](https://github.com/apache/pekko/pull/1937))
* Make calculateDelay a public method ([PR1940](https://github.com/apache/pekko/pull/1940))

### Changes

* Tweak withAttributes in Flow ([PR1658](https://github.com/apache/pekko/pull/1658))
* Handle NormalShutdownReason in MergeHub ([PR1741](https://github.com/apache/pekko/pull/1741))
* optimize recoverWith to avoid some materialization ([PR1775](https://github.com/apache/pekko/pull/1775))
* Regenerated all the source code for Protobuf using 4.29.3 ([PR1795](https://github.com/apache/pekko/pull/1795))
* Avoid materialize an empty source in switchMap ([PR1804](https://github.com/apache/pekko/pull/1804))
* Fix wrong name attribute for iterate and mapAsyncPartitionUnordered operators ([PR1869](https://github.com/apache/pekko/pull/1869))
* Change aggregateWithBoundary operator in javadsl to use Optional ([PR1876](https://github.com/apache/pekko/pull/1876))
* TLS v1.3 is now the default ([PR1901](https://github.com/apache/pekko/pull/1901))
* Set vector builder to null after stage completed to avoid leak ([PR1917](https://github.com/apache/pekko/pull/1917))
* Renamed internal Alogithm class (pekko-serialization-jackson) ([PR1932](https://github.com/apache/pekko/pull/1932))

### Dependency Changes

* netty 4.2.2.Final
* jackson 2.19.1
* lightbend/config 1.4.4
* protobuf-java 4.31.1
* slfj4 2.0.17
* jupiter-junit 5.13.3

## 1.2.0-M1

Pekko 1.2.0-M1 has some new features, performance updates and dependency upgrades. See the [GitHub Milestone](https://github.com/apache/pekko/milestone/6?closed=1) for a fuller list of changes.

This is milestone release and is aimed at testing some new changes. It is expected that there will be at least one more milestone before a full release. This release should ideally not be used in production.

### Bug Fixes

* Fix a leak in FlatMapPrefix operator ([PR1622](https://github.com/apache/pekko/pull/1622))
* Fix a leak in PrefixAndTail operator ([PR1623](https://github.com/apache/pekko/pull/1623))
* Fix occasional ordering issue in FlowWithContext#unsafeOptionalDataVia ([PR1681](https://github.com/apache/pekko/pull/1681))
* Add the missing EmptySource case to TraversalBuilder ([PR1743](https://github.com/apache/pekko/pull/1743))

### Additions

* add non-default config that allows InboundQuarantineCheck to ignore 'harmless' quarantine events ([PR1555](https://github.com/apache/pekko/pull/1555))
* New Sink.none operator ([PR1614](https://github.com/apache/pekko/pull/1614))
* Add overridden duration timeout to StreamTestKit ([PR1648](https://github.com/apache/pekko/pull/1648))
* Add Identity function to Java DSL ([PR1671](https://github.com/apache/pekko/pull/1671))
* Add support for controlling the NettyTransport's byteBuf allocator type ([PR1707](https://github.com/apache/pekko/pull/1707))

### Changes

* Make flatMapPrefix javadsl using java.util.List ([PR271](https://github.com/apache/pekko/pull/271))
* Add SchedulerTask which will be notified once cancelled ([PR1593](https://github.com/apache/pekko/pull/1593))
* Reduce loops when cleaning queue in BroadcastHub ([PR1628](https://github.com/apache/pekko/pull/1628))
* Avoid calling finalizeStage more times than once ([PR1650](https://github.com/apache/pekko/pull/1650))
* avoid boxing in zipWithIndex and fix type signature in SubSource#zipWithIndex ([PR1669](https://github.com/apache/pekko/pull/1669))
* Avoid forwarding method on ArrayDequeue in stream module ([PR1687](https://github.com/apache/pekko/pull/1687))
* Avoid forwarding method on ArrayDequeue in BatchingExecutor ([PR1688](https://github.com/apache/pekko/pull/1688))
* Enhance virtual thread support ([PR1724](https://github.com/apache/pekko/pull/1724))
* Add LoadMetrics support for virtual thread executor ([PR1734](https://github.com/apache/pekko/pull/1734))

### Dependency Changes

* jackson 2.18.2 - it is recommended for users to uptake jackson 2.18.3 when it is released due to some bug fixes ([PR1556](https://github.com/apache/pekko/pull/1556))
* netty 4.1.117.Final
* protobuf-java to 3.25.6 ([PR1748](https://github.com/apache/pekko/pull/1748))
* scala 2.12.20, 2.13.16, 3.3.5
