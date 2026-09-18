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

## Configuration Changes in Pekko 2.x

The `reference.conf` defaults have changed in a number of places. If you override any of the settings
below in your `application.conf`, or rely on their Pekko 1.x defaults, review this list.

### Changed default values

* `pekko.actor.default-dispatcher.fork-join-executor.minimum-runnable` changed from `1` to `-1`.
The value `-1` selects a JDK-aware default that maintains a minimum number of non-blocked worker
threads on newer JDKs. Set it explicitly to `1` to restore the Pekko 1.x behavior.
The internal dispatcher (`pekko.actor.internal-dispatcher.fork-join-executor`) uses the same new default.
([PR2890](https://github.com/apache/pekko/pull/2890))
* `pekko.remote.artery.propagate-harmless-quarantine-events` changed from `on` to `off`, so harmless
quarantine events are no longer propagated by default. ([PR2430](https://github.com/apache/pekko/pull/2430))
* `pekko.remote.artery.advanced.tcp-magic` changed from `["AKKA", "PEKK"]` to `["PEKK", "AKKA"]`
(see the TCP magic header note above). ([PR3425](https://github.com/apache/pekko/pull/3425))
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

### Removed configuration

* `pekko.actor.typed.timeout` was removed along with the deprecated `TypedActor` API.
* `pekko.cluster.sharding.passivate-idle-entity-after` was removed; use
`pekko.cluster.sharding.passivation.default-idle-strategy.idle-entity.timeout` instead.
* The `pekko.ssl-config` and top-level `ssl-config` sections were removed along with the
`ssl-config` library dependency.

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

@ref:[pekko-cluster-sharding-typed](../general/configuration-reference.md#config-cluster-sharding-typed),
@ref:[pekko-cluster-sharding](../general/configuration-reference.md#config-cluster-sharding) and
@ref:[pekko-distributed-data](../general/configuration-reference.md#config-distributed-data):

* `pekko.cluster.sharded-daemon-process.keep-alive-from-number-of-nodes` and
`pekko.cluster.sharded-daemon-process.keep-alive-throttle-interval` tune keep-alive pinging, which
is now performed from a limited number of nodes instead of every node.
([PR2755](https://github.com/apache/pekko/pull/2755))
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

* `pekko.serialization.jackson.compression.max-decompressed-size` bounds the size of a payload
after decompression. ([PR3491](https://github.com/apache/pekko/pull/3491))
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
