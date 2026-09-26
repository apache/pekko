# Release Notes (1.7.x)

Apache Pekko 1.7.x releases support Java 8 and above.

# 1.7.1

Pekko 1.7.1 has some bug fixes. See the [GitHub Milestone for 1.7.1](https://github.com/apache/pekko/milestone/33?closed=1) for a fuller list of changes.

### Bug Fix

* Make AbstractPersistentActorWithTimers and AbstractFSMWithStash subclassable from Java on Scala 3 ([PR3477](https://github.com/apache/pekko/pull/3477))
* Use a Seq rather than a Set to hold the TCP magic ByteStrings ([PR3498](https://github.com/apache/pekko/pull/3498))
* Bound compression-pointer hops in DNS name parsing ([PR3499](https://github.com/apache/pekko/pull/3499))
* Bound nesting depth when deserializing enclosed payloads ([PR3501](https://github.com/apache/pekko/pull/3501))
* Resolve, check and parse manifests more carefully in the Jackson serializers ([PR3517](https://github.com/apache/pekko/pull/3517))
* Don't resolve HOCON includes in config that arrived in a message ([PR3518](https://github.com/apache/pekko/pull/3518))
* Match actor selection wildcards without backtracking ([PR3519](https://github.com/apache/pekko/pull/3519))
* Bounds check the lookup table indexes in gossip ([PR3520](https://github.com/apache/pekko/pull/3520))
* Bound the number of entries in a compression table advertisement ([PR3521](https://github.com/apache/pekko/pull/3521))
* Bound Jackson payload decompression size, unlimited by default ([PR3523](https://github.com/apache/pekko/pull/3523))
* Bound inbound Artery TCP frame length at framing ([PR3525](https://github.com/apache/pekko/pull/3525))
* Bound the size a compressed payload may expand to ([PR3527](https://github.com/apache/pekko/pull/3527))

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
