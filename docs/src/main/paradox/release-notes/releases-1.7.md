# Release Notes (1.7.x)

Apache Pekko 1.7.x releases support Java 8 and above.

# 1.7.0

Pekko 1.7.0 has some bug fixes. See the [GitHub Milestone for 1.7.0](https://github.com/apache/pekko/milestone/30?closed=1) for a fuller list of changes.

### Bug Fix

* Harden EndpointReader against NonFatal dispatch errors and unwrap WrappedMessage in writer logs ([#3169](https://github.com/apache/pekko/issues/3169))
* Filter messages from remember-entities store ([PR3411](https://github.com/apache/pekko/pull/3411))

### Changes

* Support setting the ForkJoinPool minimum-runnable value ([PR3037](https://github.com/apache/pekko/pull/3037))
* Backport TcpFraming magic config ([PR3444](https://github.com/apache/pekko/pull/3444))

### Dependency Changes

* aeron 1.45.2
* netty 4.2.17.Final
* jackson 2.21.6
* protobuf-java 4.33.6
* config 1.4.9
* lz4-java 1.11.2
