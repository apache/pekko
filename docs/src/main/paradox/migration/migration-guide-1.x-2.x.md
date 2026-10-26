---
project.description: Migrating to Apache Pekko 2.0.
---
# Migration from Apache Pekko 1.x to 2.x

Apache Pekko 2.x is not @ref:[Binary Compatible](../common/binary-compatibility-rules.md) with Apache Pekko 1.x.

The major difference is that some deprecated code has been removed but there are also a small number of
breaking changes where deprecation was not feasible.

It is possible that some simple code compiled with Pekko 1.x libs will work with Pekko 2.x but you should not
rely on this.

Pekko 1.x is still maintained and major bugs will be fixed in it.

## Start by upgrading to latest Pekko 1.x releases

Some of the changes in Pekko 2.x have been backported although these changes are normally only done when they fix
important bugs.

Some additional code has been deprecated in the most recent 1.x releases and the compile warnings will help you with
moving onto better supported APIs.

## Change any code that relies on deprecated APIs in Pekko 1.x

Not every deprecated API has been removed in Pekko 2.x but many have been.

Java API users may find that they have more deprecations to deal with because the Scala API is more stable and there
were a few mistakes in the Java API where Scala classes leaked into some of the Java API methods.

### Deprecations in Pekko 2.x

* The `Source.queue` overloads that take an `OverflowStrategy` and materialize a `SourceQueueWithComplete` are
deprecated. Their asynchronous `offer` can hang indefinitely under `OverflowStrategy.backpressure` when downstream
stalls. Use `Source.queue(bufferSize)` (which materializes a `BoundedSourceQueue` with synchronous feedback),
`Source.actorRefWithBackpressure` or `MergeHub.source` instead. See
@ref:[Migrating from the deprecated Source.queue overloads](../stream/operators/Source/queue.md#migrating-from-the-deprecated-overflowstrategy-overloads)
for a replacement per `OverflowStrategy`, with code examples.

## Additional Breaking Changes in Pekko 2.x
* In the Scala DSL for Flow and Source, the `watchTermination` function call no longer needs an empty param
list before a second param list. Instead of `watchTermination(){ ... }`, you now must use `watchTermination{ ... }`.
([PR2378](https://github.com/apache/pekko/pull/2378))
* In the Java API, `FSMTransitionHandlerBuilder.build` and `FSMTransitionHandlerBuilder.state` now use
`pekko.japi.Pair` instead of `scala.Tuple2`. Java users need to update their code to use `Pair` instead of `Tuple2`.
([PR3378](https://github.com/apache/pekko/pull/3378))
* The Java DSL `SourceWithContext` graph shape now uses `pekko.japi.Pair` instead of `scala.Tuple2`.
Java code that passes a `SourceWithContext` directly to `GraphDSL` must use `Pair`-typed stages.
Code that converts it with `asSource()` already uses `Pair` and requires no changes.
([PR3388](https://github.com/apache/pekko/pull/3388))
* `ReceiveTimeout` changed from a `case object` singleton to a `final case class` that carries the configured
timeout duration. Scala pattern matches must use a type pattern (`case timeout: ReceiveTimeout =>`) instead of a
stable identifier pattern, and the `ReceiveTimeout.getInstance()` method has been removed from the Java API;
Java users should match on `ReceiveTimeout.class` instead.
([PR3399](https://github.com/apache/pekko/pull/3399))
* In pekko-remote Artery comms, `"PEKK"` is now the default magic header
(`pekko.remote.artery.advanced.tcp-magic`). See
@ref:[Changing TCP magic header](../additional/rolling-updates.md#changing-tcp-magic-header) for what this means for
rolling updates. ([PR3425](https://github.com/apache/pekko/pull/3425))
* `RemoveInternalClusterShardingData` no longer supports removing Cluster Sharding coordinator data stored
by Akka 2.3.x. The `-2.3` program argument and the `remove2dot3Data` parameter of
`RemoveInternalClusterShardingData.remove` have been removed. ([PR3608](https://github.com/apache/pekko/pull/3608))
* In pekko-persistence-testkit, the `PersistenceTestKitReadJournal` tag queries (`eventsByTag`,
`currentEventsByTag`) and the live `eventsBySlices` query now put a `TimestampOffset` in their envelopes instead of
`Sequence`. Code that cast these offsets to `Sequence` needs updating; the per-persistence-id sequence number is
still available as `envelope.sequenceNr`. These queries also now accept a `TimestampOffset` to resume from.
([PR3615](https://github.com/apache/pekko/pull/3615))
* `ShardCoordinator.AbstractShardAllocationStrategy`, the Java API for custom shard allocation strategies, no longer
leaks Scala types. Its `allocateShard` and `rebalance` methods now return a `java.util.concurrent.CompletionStage`
instead of a Scala `Future`, and the current shard allocations are passed as a
`java.util.Map<ActorRef, ? extends List<String>>` instead of a `java.util.Map` of
`scala.collection.immutable.IndexedSeq<String>`. Update both method signatures and recompile Java subclasses
against Pekko 2.x. Subclasses compiled against Pekko 1.x must not be used unchanged: they do not implement the
new methods and will fail at runtime with `AbstractMethodError` when the coordinator invokes the strategy,
preventing shard allocation or rebalancing. Scala implementations of `ShardCoordinator.ShardAllocationStrategy`
are unaffected. Java strategies that need initialization on the coordinator node can additionally implement
`ShardCoordinator.StartableAllocationStrategy` and override `void start()`; the coordinator calls it before
`allocateShard` or `rebalance`.
([PR3677](https://github.com/apache/pekko/pull/3677))

## Dependency Changes in Pekko 2.x
* Java 17 is the minimum supported Java version and Scala 2.12 is no longer supported. Pekko 2.x is published for
Scala 2.13 and Scala 3.3 (LTS), so Scala 3 users need Scala 3.3.x or later. Pekko 2.x is also tested with
Scala 3.9.
* Agrona was updated from 1.x to 2.x, which
[means](https://github.com/aeron-io/agrona/wiki/Change-Log#200-2024-12-17) you may have to add the JVM option
`--add-opens java.base/jdk.internal.misc=ALL-UNNAMED` if you use Pekko Remote with Artery. This applies to
applications on the classpath as well as those that use the Java Module System (`ALL-UNNAMED` covers classpath code).
* pekko-serialization-jackson depends on `at.yawk.lz4:lz4-java` instead of the unmaintained `org.lz4:lz4-java`.
This change was made in Pekko 1.4.0, so you will only notice it if you are upgrading from an earlier Pekko 1.x
release. The fork is a drop-in replacement with important bug fixes. If your build also depends on
`org.lz4:lz4-java`, consider excluding it so that the two jars are not both on the classpath.
([PR2537](https://github.com/apache/pekko/pull/2537))
* The `ssl-config` library is no longer a dependency of Pekko and the `ssl-config` configuration sections have
been removed (see Removed configuration below). ([PR2127](https://github.com/apache/pekko/pull/2127))

* The deprecated `PekkoSSLConfig` convenience API and the stream methods accepting it were removed.
Initialize an `SSLContext` with the required key and trust managers, and supply a factory that creates a new
`javax.net.ssl.SSLEngine` for each connection. Set its client or server mode and apply the cipher suites,
protocols and client-authentication settings previously passed in `NegotiateNewSession` directly to the engine
or its `SSLParameters`.
Client factories must preserve peer identity checks: use `sslContext.createSSLEngine(host, port)`, call
`setUseClientMode(true)`, and enable hostname verification with `setEndpointIdentificationAlgorithm("HTTPS")`
on the engine's `SSLParameters`. For DNS names, also configure SNI with `SSLParameters.setServerNames`
containing an `SNIHostName` for that hostname. Apply those parameters with `SSLEngine.setSSLParameters` before returning the engine.
The factory is accepted by
@scala[@scaladoc[TLS.apply](org.apache.pekko.stream.scaladsl.TLS$)]@java[@javadoc[TLS.create](org.apache.pekko.stream.javadsl.TLS)]
and the current @apidoc[Tcp](stream.*.Tcp) TLS methods.
Replace `outgoingTlsConnection` with `outgoingConnectionWithTls` and `bindTls` with `bindWithTls`.
@scala[Also replace `bindAndHandleTls` with `bindAndHandleWithTls`.]
See the Scala and Java @ref:[TLS examples](../stream/stream-io.md#tls) for general engine setup,
adding the client hostname-verification and SNI settings described above. ([PR2127](https://github.com/apache/pekko/pull/2127))

## Configuration Changes in Pekko 2.x

The `reference.conf` defaults have changed in a number of places. If you override any of the settings
below in your `application.conf`, or rely on their Pekko 1.x defaults, review this list.

Some of these settings were also backported to Pekko 1.x releases. Those entries note the Pekko version
in which the setting first appeared, so if you are already on that version or later you may have seen
them before.

### Changed default values

* `pekko.actor.default-dispatcher.fork-join-executor.minimum-runnable` changed from `1` to `-1`.
The setting first appeared in Pekko 1.7.0, with a default of `1`.
The value `-1` selects a JDK-aware default: on JDK 25 and later the effective value is
`min(8, max(1, parallelism / 2))`, and on earlier JDKs it is `1`, so the behavior only changes on
JDK 25 and later. Set it explicitly to `1` to restore the Pekko 1.x behavior on all JDKs.
The internal dispatcher (`pekko.actor.internal-dispatcher.fork-join-executor`) uses the same new default.
([PR2890](https://github.com/apache/pekko/pull/2890))
* `pekko.remote.artery.propagate-harmless-quarantine-events` changed from `on` to `off`, so harmless
quarantine events are no longer propagated by default. ([PR2430](https://github.com/apache/pekko/pull/2430))
* `pekko.remote.artery.advanced.tcp-magic` changed from `["AKKA", "PEKK"]` to `["PEKK", "AKKA"]`
(see the TCP magic header note above). The setting first appeared in Pekko 1.7.0, with a default of
`["AKKA", "PEKK"]`. ([PR3425](https://github.com/apache/pekko/pull/3425))
* Artery compression heavy hitter detection is controlled by the new setting
`pekko.remote.artery.advanced.compression.frequency-sketch-implementation`, which defaults to
`"fast-frequency-sketch"` (a smaller, aging sketch). Set it to `"count-min-sketch"` to restore the
Pekko 1.x implementation. ([PR3023](https://github.com/apache/pekko/pull/3023))
* Persistence plugins no longer use the dedicated `pekko.persistence.dispatchers` by default. The
`plugin-dispatcher` and `replay-dispatcher` settings in the journal and snapshot-store plugin
fallbacks changed from `pekko.persistence.dispatchers.default-plugin-dispatcher` /
`pekko.persistence.dispatchers.default-replay-dispatcher` to `pekko.actor.default-dispatcher`.
The `pekko.persistence.dispatchers` definitions are deprecated; plugins that need a custom
dispatcher should define their own. ([PR2482](https://github.com/apache/pekko/pull/2482))
* `pekko.serialization.jackson.read.max-document-length` and
`pekko.serialization.jackson.read.max-token-count` changed from `-1` to `unlimited`, a value they now
accept. Negative numbers such as `-1` still mean unlimited, so the effective limits are unchanged and
existing overrides keep working. The same applies under `pekko.serialization.jackson3`.
([PR3522](https://github.com/apache/pekko/pull/3522))

### Changed behavior

* `MergeLatest` now defaults to conflating updates while downstream is back-pressured, retaining only the
latest value from each input instead of buffering every update. This bounds the state retained by
`MergeLatest` itself; it does not bound buffers in upstream stages. To preserve the previous behavior,
set `pekko.stream.materializer.merge-latest.mode = legacy`. ([#3592](https://github.com/apache/pekko/issues/3592))
* `pekko.remote.artery.ssl.rotating-keys-engine.ca-cert-file` may now bundle more than one
certificate (for example a root CA and its intermediates, or several roots while a CA is being
rotated); all of them are trusted. Pekko 1.x only loaded the first certificate in the file. The
setting and its default are unchanged. If your CA file contains certificates you do not intend to
trust, remove them before upgrading. ([PR3528](https://github.com/apache/pekko/pull/3528))

### Removed configuration

* `pekko.actor.typed.timeout` was removed along with the deprecated `TypedActor` API.
([PR1969](https://github.com/apache/pekko/pull/1969))
* `pekko.cluster.sharding.passivate-idle-entity-after` was removed; use
`pekko.cluster.sharding.passivation.default-idle-strategy.idle-entity.timeout` instead.
([PR3026](https://github.com/apache/pekko/pull/3026))
* The `pekko.ssl-config` and top-level `ssl-config` sections were removed along with the
`ssl-config` library dependency. ([PR2127](https://github.com/apache/pekko/pull/2127))

### New configuration

The full `reference.conf` for each module, with descriptions of every setting, is listed in the
@ref:[default configuration reference](../general/configuration-reference.md).

@ref:[pekko-actor](../general/configuration-reference.md#config-pekko-actor):

* `pekko.actor.default-dispatcher.fork-join-executor.virtual-thread-start-number`,
`pekko.actor.default-dispatcher.thread-pool-executor.virtual-thread-start-number` and
`pekko.actor.default-dispatcher.virtual-thread-executor.virtual-thread-start-number` control the
starting id for virtual threads created by a dispatcher. ([PR2242](https://github.com/apache/pekko/pull/2242))
* `pekko.actor.default-dispatcher.thread-pool-executor.virtualize` allows a thread-pool-executor
based dispatcher to run on virtual threads (JDK 21+). The same settings were added to
`pekko.actor.default-blocking-io-dispatcher.thread-pool-executor`.
([PR2169](https://github.com/apache/pekko/pull/2169))
* `pekko.scheduled-clock-interval` controls how frequently the clock used by recency-based
passivation strategies is updated. ([PR2766](https://github.com/apache/pekko/pull/2766))
* The async DNS resolver actors now run on the DNS dispatcher via the new deployment entries
`pekko.actor.deployment."/IO-DNS/async-dns/*"` and (in
@ref:[pekko-discovery](../general/configuration-reference.md#config-pekko-discovery))
`pekko.actor.deployment."/SD-DNS/async-dns/*"`. ([PR2895](https://github.com/apache/pekko/pull/2895))
* `pekko.actor.serialization-max-nesting-depth` (default `32`) bounds the nesting depth when a
serialized payload encloses another serialized payload. First appeared in Pekko 1.7.1.
([PR3500](https://github.com/apache/pekko/pull/3500))
* `pekko.serialization.max-decompressed-size` (default `unlimited`) bounds the size a gzipped payload
may expand to when the cluster, cluster-metrics, cluster-sharding, cluster-tools and distributed-data
serializers decompress it. First appeared in Pekko 1.7.1.
([PR3502](https://github.com/apache/pekko/pull/3502))

@ref:[pekko-cluster-sharding-typed](../general/configuration-reference.md#config-cluster-sharding-typed),
@ref:[pekko-cluster-sharding](../general/configuration-reference.md#config-cluster-sharding) and
@ref:[pekko-distributed-data](../general/configuration-reference.md#config-distributed-data):

* `pekko.cluster.sharded-daemon-process.keep-alive-from-number-of-nodes` and
`pekko.cluster.sharded-daemon-process.keep-alive-throttle-interval` tune keep-alive pinging, which
is now performed from a limited number of nodes instead of every node.
([PR2734](https://github.com/apache/pekko/pull/2734))
* `pekko.cluster.sharding.healthcheck.disabled-after` disables the sharding health check after the
configured duration post member-up. ([PR2785](https://github.com/apache/pekko/pull/2785))
* `pekko.cluster.distributed-data.expire-keys-after-inactivity` configures automatic expiry of
inactive Distributed Data keys. ([PR2733](https://github.com/apache/pekko/pull/2733))

@ref:[pekko-persistence](../general/configuration-reference.md#config-pekko-persistence),
@ref:[pekko-persistence-typed](../general/configuration-reference.md#config-pekko-persistence-typed) and
@ref:[pekko-persistence-query](../general/configuration-reference.md#config-pekko-persistence-query):

* `pekko.persistence.query.events-by-slice-firehose` is a new section configuring the
events-by-slice firehose query that fans out one shared journal query to many consumers.
([PR3277](https://github.com/apache/pekko/pull/3277))
* `pekko.persistence.typed.event-writer.max-batch-size` and
`pekko.persistence.typed.event-writer.ask-timeout` configure the event writer.
([PR3432](https://github.com/apache/pekko/pull/3432))
* `pekko.persistence.typed.event-writer.fill-sequence-number-gaps` (default `off`) makes the event
writer fill gaps in sequence numbers with `FilteredPayload` events, and
`pekko.persistence.typed.event-writer.latest-sequence-number-cache-capacity` (default `1000`) sets how
many persistence ids it keeps the latest sequence number in memory for when gap filling is enabled.
([PR3653](https://github.com/apache/pekko/pull/3653))
* `replay-batch-size` in the journal plugin fallback bounds the number of replayed events queued in
a recovering persistent actor's mailbox. ([PR3325](https://github.com/apache/pekko/pull/3325))
* `only-one-snapshot` in the snapshot-store plugin fallback enables retention optimizations for
snapshot stores that only keep the latest snapshot. ([PR2767](https://github.com/apache/pekko/pull/2767))
* `pekko.persistence.journal.inmem.delay-writes` can add an artificial write delay in tests.
([PR3432](https://github.com/apache/pekko/pull/3432))

@ref:[pekko-remote classic](../general/configuration-reference.md#config-pekko-remote) and
@ref:[pekko-remote Artery](../general/configuration-reference.md#config-pekko-remote-artery):

* `pekko.remote.classic.passive-connection-buffer-size` bounds message buffering during a passive
connection handoff. ([PR3361](https://github.com/apache/pekko/pull/3361))
* `pekko.remote.classic.netty.ssl.security.hostname-verification` enables TLS hostname verification
for classic remoting. ([PR3164](https://github.com/apache/pekko/pull/3164))
* `pekko.remote.artery.advanced.shutdown-streams-timeout` bounds the graceful drain of Artery
streams during shutdown. ([PR3317](https://github.com/apache/pekko/pull/3317))
* `pekko.remote.artery.ssl.rotating-keys-engine.keystore-password` sets the password for the
in-memory keystore used to wrap PEM-loaded keys; override it in production.
([PR3397](https://github.com/apache/pekko/pull/3397))

@ref:[pekko-serialization-jackson](../serialization-jackson.md):

* `pekko.serialization.jackson.compression.max-decompressed-size` (default `unlimited`) bounds the size
of a payload after decompression. The same setting exists under `pekko.serialization.jackson3`.
First appeared in Pekko 1.7.1. ([PR3491](https://github.com/apache/pekko/pull/3491),
[PR3515](https://github.com/apache/pekko/pull/3515))
* The new `pekko-serialization-jackson3` module (based on Jackson 3) is configured under
`pekko.serialization.jackson3`, mirroring the `pekko.serialization.jackson` settings.
([PR2348](https://github.com/apache/pekko/pull/2348))

@ref:[pekko-stream](../general/configuration-reference.md#config-pekko-stream):

* `pekko.stream.materializer.stage-errors-default-log-level` controls the log level used for
stream stage errors. ([PR2805](https://github.com/apache/pekko/pull/2805))
* `pekko.stream.materializer.stage-actor-drain-batch` bounds the number of stage-actor messages
drained per envelope for lazily materialized stage actors. ([PR3035](https://github.com/apache/pekko/pull/3035))
* `pekko.stream.materializer.tls.engine` selects the stream TLS engine implementation
(`"legacy-actor"` or the opt-in `"graph-stage"`). ([PR2878](https://github.com/apache/pekko/pull/2878))
* `pekko.stream.materializer.merge-latest.mode` selects the default `latest` conflating behavior or
`legacy` buffering behavior for MergeLatest. ([#3592](https://github.com/apache/pekko/issues/3592))

### Serialization bindings

These serialization changes matter if you do a rolling update from Pekko 1.x to 2.x, or need to roll
back:

* `org.apache.pekko.util.ByteString$ByteString2`, a new `ByteString` implementation, is bound to the
existing `primitive-bytestring` serializer. That serializer writes no manifest, so Pekko 1.x nodes read
these messages as an ordinary `ByteString`. ([PR2924](https://github.com/apache/pekko/pull/2924))
* The `typed-sharding` serializer is now bound to the internal `ClusterShardingTypedSerializable` marker
instead of `ShardingEnvelope`. `ShardingEnvelope` is serialized as before, but the serializer also
handles new Sharded Daemon Process messages (its state, and the commands for getting and changing the
number of processes) that Pekko 1.x nodes cannot deserialize. ([PR2755](https://github.com/apache/pekko/pull/2755))
* When remoting is enabled and ScalaPB is on the classpath, ScalaPB generated messages
(`scalapb.GeneratedMessage`) are now bound to the `proto` serializer by default. If your ScalaPB
messages were serialized with another serializer in Pekko 1.x, mixed-version nodes would disagree
during a rolling update. To keep the Pekko 1.x behavior, disable the new binding:
`pekko.actor.serialization-bindings { "scalapb.GeneratedMessage" = none }`.
([PR3651](https://github.com/apache/pekko/pull/3651))
* The new `FilteredPayload` placeholder, which is stored in the journal in place of a filtered event
(for example with events-by-slice queries and projections), has a new serializer with identifier `34`.
Pekko 1.x cannot deserialize it, so do not run Pekko 1.x against a journal once such events have been
written. ([PR3275](https://github.com/apache/pekko/pull/3275))
The same serializer also handles the new `SerializedEvent`, used to write events that are already
serialized; Pekko 1.x cannot deserialize that either, so do not send `SerializedEvent` messages to
Pekko 1.x nodes during a rolling update. ([PR3650](https://github.com/apache/pekko/pull/3650))
* `pekko-serialization-jackson` (and `pekko-serialization-jackson3`) now provide predefined
`JsonSerializable` and `CborSerializable` marker traits, bound by default to `jackson-json` and
`jackson-cbor`. Your own marker traits and bindings keep working. If you switch messages to the
predefined traits, Pekko 1.x nodes cannot load those classes, so only do that once no Pekko 1.x nodes
remain in the cluster. ([PR3649](https://github.com/apache/pekko/pull/3649))
* Only one of `pekko-serialization-jackson` and `pekko-serialization-jackson3` can be on the
classpath. Both register serializers with the same names and identifiers, and the serializers now fail
at ActorSystem startup if both modules are present. The two modules have different default setups
(Jackson 2 and Jackson 3 differ in their default features and registered modules), so the same message
can be serialized differently, and data written by one may not be read correctly by the other. In a
Cluster or Remoting setup, make sure all Pekko nodes use the same Jackson serializer module.
([PR3673](https://github.com/apache/pekko/pull/3673))
