# Release Notes (1.2.x)

Apache Pekko 1.2.x releases support Java 8 and above.

# 1.2.0

Pekko 1.2.0 has some new features, performance updates and dependency upgrades. See [GitHub Milestone for 1.2.0-M1](https://github.com/apache/pekko/milestone/6?closed=1), [GitHub Milestone for 1.2.0-M2](https://github.com/apache/pekko/milestone/15?closed=1) and [GitHub Milestone for 1.2.0](https://github.com/apache/pekko/milestone/16?closed=1) for a fuller list of changes.

Most of the changes appeared in the milestone releases (1.2.0-M1 and 1.2.0-M2) but some additional changes were made for the 1.2.0 release. These extra changes include deprecating some methods that will be removed in a future release.

### Bug Fixes

* Fix a leak in FlatMapPrefix operator ([PR1622](https://github.com/apache/pekko/pull/1622))
* Fix a leak in PrefixAndTail operator ([PR1623](https://github.com/apache/pekko/pull/1623))
* Fix occasional ordering issue in FlowWithContext#unsafeOptionalDataVia ([PR1681](https://github.com/apache/pekko/pull/1681))
* Add the missing EmptySource case to TraversalBuilder ([PR1743](https://github.com/apache/pekko/pull/1743))
* Issue forming mixed Akka/Pekko cluster when classic remoting with SSL/TLS is used ([PR1857](https://github.com/apache/pekko/pull/1857))
* Join cluster check adjusted to support Akka nodes ([PR1866](https://github.com/apache/pekko/pull/1866), [PR1877](https://github.com/apache/pekko/pull/1877))
    * If you are attempting to mix Akka and Pekko nodes in a cluster, it is still recommended to disable the join cluster check but these changes may be enough to get it work ([docs](https://cwiki.apache.org/confluence/display/PEKKO/Pekko+Akka+Compatibility)).
* BroadcastHub drops elements due to register/unregister race ([PR1841](https://github.com/apache/pekko/pull/1841))
* Fix issue with number deserialization in pekko-cluster-metrics ([PR1899](https://github.com/apache/pekko/pull/1899))
* Fix typed persistence stack overflow with many read only commands ([PR1919](https://github.com/apache/pekko/pull/1919))
* Allow overriding dispatcher in mapWithResource ([PR1949](https://github.com/apache/pekko/pull/1949))
* Fix issue with ByteBuffer cleaner only working with Java 8 ([PR2020](https://github.com/apache/pekko/pull/2020))

### Additions

* add non-default config that allows InboundQuarantineCheck to ignore 'harmless' quarantine events ([PR1555](https://github.com/apache/pekko/pull/1555))
* New Sink.none operator ([PR1614](https://github.com/apache/pekko/pull/1614))
* Add overridden duration timeout to StreamTestKit ([PR1648](https://github.com/apache/pekko/pull/1648))
* Add Identity function to Java DSL ([PR1671](https://github.com/apache/pekko/pull/1671))
* Add support for controlling the NettyTransport's byteBuf allocator type ([PR1707](https://github.com/apache/pekko/pull/1707))
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
* Add JournalPersistFailed and JournalPersistRejected signals ([PR1961](https://github.com/apache/pekko/pull/1961))
* Make calculateDelay a public method ([PR1940](https://github.com/apache/pekko/pull/1940))
* Allow disabling AsyncWriteJournal.Resequencer to improve latency ([#2026](https://github.com/apache/pekko/issues/2026))
* Add CompletionStages helper ([PR2049](https://github.com/apache/pekko/pull/2049))

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
* Tweak withAttributes in Flow ([PR1658](https://github.com/apache/pekko/pull/1658))
* Handle NormalShutdownReason in MergeHub ([PR1741](https://github.com/apache/pekko/pull/1741))
* optimize recoverWith to avoid some materialization ([PR1775](https://github.com/apache/pekko/pull/1775))
* Regenerated all the source code for Protobuf using 4.32.0 ([PR1795](https://github.com/apache/pekko/pull/1795), [PR2036](https://github.com/apache/pekko/pull/2036))
* Avoid materialize an empty source in switchMap ([PR1804](https://github.com/apache/pekko/pull/1804))
* Fix wrong name attribute for iterate and mapAsyncPartitionUnordered operators ([PR1869](https://github.com/apache/pekko/pull/1869))
* Change aggregateWithBoundary operator in javadsl to use Optional ([PR1876](https://github.com/apache/pekko/pull/1876))
* TLS v1.3 is now the default ([PR1901](https://github.com/apache/pekko/pull/1901))
* Set vector builder to null after stage completed to avoid leak ([PR1917](https://github.com/apache/pekko/pull/1917))
* Renamed internal Algorithm class (pekko-serialization-jackson) ([PR1932](https://github.com/apache/pekko/pull/1932))
* Deprecate FunctionalInterfaces that are being removed in pekko 2.0.0 ([PR2004](https://github.com/apache/pekko/pull/2004), [PR2075](https://github.com/apache/pekko/pull/2075)) 
* Disable batch if isVirtualized ([PR2046](https://github.com/apache/pekko/pull/2046))
* Deprecate more methods in Futures ([PR2048](https://github.com/apache/pekko/pull/2048))
* Remove incorrect deprecation in IOResult ([PR2054](https://github.com/apache/pekko/pull/2054))
* Make timeoutCompletionStage accept Java Duration ([PR2063](https://github.com/apache/pekko/pull/2063))

### Dependency Changes

* netty 4.2.4.Final
* jackson 2.19.2
* lightbend/config 1.4.4
* protobuf-java 4.32.0
* slfj4 2.0.17
* jupiter-junit 5.13.3
* scala 2.12.20, 2.13.16, 3.3.6

### Known Issues

This release breaks binary compatibility for [pekko-persistence-cassandra](https://github.com/apache/pekko-persistence-cassandra/issues/305)
and users of that lib will need to avoid Pekko 1.2.0 release. We will fix
this in the 1.2.1 release.
