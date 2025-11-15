# Release Notes (1.3.x)

Apache Pekko 1.3.x releases support Java 8 and above.

# 1.3.0

Pekko 1.3.0 has some bug fixes, new features, performance updates and dependency upgrades. See the [GitHub Milestone for 1.3.0](https://github.com/apache/pekko/milestone/21?closed=1) for a fuller list of changes.

This release includes a number of changes from Akka 2.7.0, which have recently become available under the Apache License, Version 2.0.

### Bug Fixes

* Fix issues with OSGi imports for Pekko packages ([PR2312](https://github.com/apache/pekko/pull/2312))
* Fix close in cancel for statefulMap ([PR2388](https://github.com/apache/pekko/pull/2388))

### Additions

* Add Flow/Source#onErrorResume for Java DSL ([PR2120](https://github.com/apache/pekko/pull/2120))
* Add Sink#count operator ([PR2244](https://github.com/apache/pekko/pull/2244))
* Add Sink#source operator ([PR2250](https://github.com/apache/pekko/pull/2250))
* ByteString: new indexOf overloaded method that allows from and to ([PR2272](https://github.com/apache/pekko/pull/2272))
* JavaDSL TestKit: add shutdownActorSystem that takes Java Duration params ([PR2277](https://github.com/apache/pekko/pull/2277))
* Add Flow#onErrorContinue operator ([PR2322](https://github.com/apache/pekko/pull/2322))
* Add missing onErrorResume to SubFlow and SubSource ([PR2336](https://github.com/apache/pekko/pull/2336))
* Add more recover operators for Java DSL ([PR2337](https://github.com/apache/pekko/pull/2337))
* Add doOnFirst operator ([PR2363](https://github.com/apache/pekko/pull/2363))
* Add doOnCancel operator ([PR2375](https://github.com/apache/pekko/pull/2375))
* Add actor-typed Java DSL AbstractMatchingBehavior ([PR2379](https://github.com/apache/pekko/pull/2379))
* Add fromOption operator ([PR2413](https://github.com/apache/pekko/pull/2413))
* Add mapOption operator ([PR2414](https://github.com/apache/pekko/pull/2414))
* Add Source#items ([PR2429](https://github.com/apache/pekko/pull/2429))
* persistence-typed: custom stash support ([PR2433](https://github.com/apache/pekko/pull/2433))
* Add effectful asking support in typed BehaviorTestKit ([PR2450](https://github.com/apache/pekko/pull/2450))
* Add asking support to BehaviorTestKit ([PR2453](https://github.com/apache/pekko/pull/2453))
* Add Source#apply for Array ([PR2474](https://github.com/apache/pekko/pull/2474))
* Add PersistenceProbeBehavior for testing Persistence Behaviors ([PR2456](https://github.com/apache/pekko/pull/2456), [PR2494](https://github.com/apache/pekko/pull/2494))
* Add close method (blocking) and AutoCloseable interface to ActorSystem ([PR2486](https://github.com/apache/pekko/pull/2486))

### Changes

* Some ByteString performance improvements ([PR2346](https://github.com/apache/pekko/pull/2346), [PR2347](https://github.com/apache/pekko/pull/2347))
* Change pekko.ssl-config.protocol default to TLSv1.3 ([PR2360](https://github.com/apache/pekko/pull/2360))
* Persistence Testkit: emit DeletedDurableState for deleted objects ([PR2397](https://github.com/apache/pekko/pull/2397))
* Rename gunzip to gzipDecompress ([PR2405](https://github.com/apache/pekko/pull/2405))
* Regenerate Protobuf based source files with 4.33 ([PR2410](https://github.com/apache/pekko/pull/2410))
* Deprecate stream testkit's probe methods ([PR2439](https://github.com/apache/pekko/pull/2439))
* Compare required RC and M versions if present ([PR2441](https://github.com/apache/pekko/pull/2441))

### Dependency Changes

* netty 4.2.7.Final
* jackson 2.20.1
* protobuf-java 4.33.0
* ssl-config 0.7.1
* scala 2.13.17, 3.3.7
