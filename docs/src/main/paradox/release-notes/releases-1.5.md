# Release Notes (1.5.x)

Apache Pekko 1.5.x releases support Java 8 and above.

# 1.5.0

Pekko 1.5.0 has some bug fixes and small enhancements. See the [GitHub Milestone for 1.5.0](https://github.com/apache/pekko/milestone/26?closed=1) for a fuller list of changes.

### Bug Fixes

* RecoverWith operator break of semantics in 1.2.0 release ([#2620](https://github.com/apache/pekko/issues/2620))
* Release lease on shard stop ([PR2740](https://github.com/apache/pekko/pull/2740))
* Source.combine single source with type-transforming fan-in strategies ([PR2746](https://github.com/apache/pekko/pull/2746))

### Additions

* Add Sink.eagerFutureSink to avoid NeverMaterializedException on empty streams ([PR2722](https://github.com/apache/pekko/pull/2722))

### Changes

* Deprecate Source#future in javadsl ([PR2555](https://github.com/apache/pekko/pull/2555))
* Optimize Source#future and Source#futureSource in scaladsl ([PR2583](https://github.com/apache/pekko/pull/2583))
* Use array list for better performance in BroadcastHub ([PR2596](https://github.com/apache/pekko/pull/2596))

### Dependency Changes

* netty 4.2.10.Final
* jackson 2.21.2
* protobuf-java 4.33.5
* config 1.4.6
* lz4-java 1.10.4
