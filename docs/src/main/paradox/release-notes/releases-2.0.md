# Release Notes (2.0.x)

Apache Pekko 2.0.x releases support Java 17 and above.

## 2.0.0

Apache Pekko 2.0.0 is a new major release. It is not binary compatible with Pekko 1.x and it also has some source
incompatible changes. Please read the @ref:[Migration Guide](../migration/migration-guide-1.x-2.x.md) before upgrading.

The changes in this release were previewed in a series of milestone releases. See the GitHub milestones for
[2.0.0-M1](https://github.com/apache/pekko/milestone/5?closed=1),
[2.0.0-M2](https://github.com/apache/pekko/milestone/19?closed=1),
[2.0.0-M3](https://github.com/apache/pekko/milestone/28?closed=1),
[2.0.0-M4](https://github.com/apache/pekko/milestone/31?closed=1) and
[2.0.0-M5](https://github.com/apache/pekko/milestone/35?closed=1) for a fuller list of changes.

Some of the bug fixes listed below have also been backported to Pekko 1.x releases.

### Main changes

* Java 17 is the new minimum version (Java 8 and 11 are no longer supported) ([#1730](https://github.com/apache/pekko/issues/1730), [PR1967](https://github.com/apache/pekko/pull/1967))
* Scala 2.12 support has been dropped. Pekko 2.0.0 is published for Scala 2.13 and Scala 3 ([#39](https://github.com/apache/pekko/issues/39), [PR1986](https://github.com/apache/pekko/pull/1986))
* A lot of code that was deprecated in Pekko 1.x (and in Akka before that) has been removed - see the Removed section below
* Big change for Java DSL users due to the removal of `pekko.japi.Function` (and related classes, such as `pekko.japi.function.FI` and `JAPI`) in favour of `pekko.japi.function.Function` and the other `pekko.japi.function` types. Lambdas should recompile ok but if you declared variables or functions explicitly, then you may need to change your imports ([PR2001](https://github.com/apache/pekko/pull/2001), [PR2064](https://github.com/apache/pekko/pull/2064), [PR2078](https://github.com/apache/pekko/pull/2078), [PR2079](https://github.com/apache/pekko/pull/2079), [PR2143](https://github.com/apache/pekko/pull/2143), [PR2198](https://github.com/apache/pekko/pull/2198))
* Java APIs no longer leak Scala types: Scala `Future`, `Option`, `Tuple2` and Scala collections have been replaced with `CompletionStage`, `Optional`, `pekko.japi.Pair` and Java collections in a number of Java DSL methods, including the pekko-persistence Java APIs ([PR1419](https://github.com/apache/pekko/pull/1419), [PR2007](https://github.com/apache/pekko/pull/2007), [PR2009](https://github.com/apache/pekko/pull/2009), [PR2092](https://github.com/apache/pekko/pull/2092), [#2086](https://github.com/apache/pekko/issues/2086), [PR3378](https://github.com/apache/pekko/pull/3378), [PR3388](https://github.com/apache/pekko/pull/3388))
* Added many Akka changes that have recently become Apache Licensed (up to and including Akka 2.8.5) - see the Ported from Akka section below
* `sun.misc.Unsafe` is no longer used. The code has been migrated to use VarHandles ([PR1892](https://github.com/apache/pekko/pull/1892), [PR1894](https://github.com/apache/pekko/pull/1894), [PR1990](https://github.com/apache/pekko/pull/1990), [PR1995](https://github.com/apache/pekko/pull/1995), [PR3007](https://github.com/apache/pekko/pull/3007), [PR3008](https://github.com/apache/pekko/pull/3008))
* Reflection usage in core modules has been replaced with MethodHandles/VarHandles where possible ([PR3300](https://github.com/apache/pekko/pull/3300), [PR3312](https://github.com/apache/pekko/pull/3312))
* New pekko-serialization-jackson3 module. Users who are happy with pekko-serialization-jackson, which uses Jackson 2, can stick with that ([PR2348](https://github.com/apache/pekko/pull/2348))
* Switch to `PEKK` as the default magic header in pekko-remote Artery comms. See @ref:[Changing TCP magic header](../additional/rolling-updates.md#changing-tcp-magic-header) for what this means for rolling updates ([PR3425](https://github.com/apache/pekko/pull/3425))
* Java 21 and 25 support improvements, including virtual thread support for more dispatcher types and JDK-aware ForkJoinPool tuning ([#2232](https://github.com/apache/pekko/issues/2232), [PR2169](https://github.com/apache/pekko/pull/2169), [PR2242](https://github.com/apache/pekko/pull/2242), [PR2871](https://github.com/apache/pekko/pull/2871), [PR2890](https://github.com/apache/pekko/pull/2890))
* Added [JSpecify](https://jspecify.dev/) nullness annotations to the Java DSL APIs of streams, persistence, actor-typed, cluster-sharding-typed and testkit ([#2563](https://github.com/apache/pekko/issues/2563), [PR2617](https://github.com/apache/pekko/pull/2617), [PR2850](https://github.com/apache/pekko/pull/2850), [PR3380](https://github.com/apache/pekko/pull/3380))
* A number of security hardening changes - see the Security Hardening section below
* A number of configuration defaults have changed. See the Configuration Changes section of the @ref:[Migration Guide](../migration/migration-guide-1.x-2.x.md)

### Upgrade notes

* Agrona was updated from 1.x to 2.x, which [means](https://github.com/aeron-io/agrona/wiki/Change-Log#200-2024-12-17) you may have to add `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED` if you use the Java Module System and Pekko Remote ([PR2391](https://github.com/apache/pekko/pull/2391))
* In the Scala DSL, `watchTermination(){ ... }` must now be written as `watchTermination{ ... }` ([PR2378](https://github.com/apache/pekko/pull/2378))
* `ReceiveTimeout` changed from a `case object` to a `final case class` that carries the configured timeout duration. Scala pattern matches must use a type pattern and Java users should match on `ReceiveTimeout.class` ([PR3399](https://github.com/apache/pekko/pull/3399))
* `pekko.remote.artery.propagate-harmless-quarantine-events` now defaults to `off` ([#2141](https://github.com/apache/pekko/issues/2141), [PR2430](https://github.com/apache/pekko/pull/2430))
* Persistence plugins now use `pekko.actor.default-dispatcher` by default instead of the dedicated `pekko.persistence.dispatchers` ([PR2482](https://github.com/apache/pekko/pull/2482))
* Artery compression now uses a `FastFrequencySketch` instead of a `CountMinSketch` for heavy hitter detection by default ([PR3023](https://github.com/apache/pekko/pull/3023))
* The Java test kits now support JUnit Jupiter (JUnit 5 and 6). Pekko's own Java tests have been migrated from JUnit 4 ([#967](https://github.com/apache/pekko/issues/967), [PR2289](https://github.com/apache/pekko/pull/2289), [PR2724](https://github.com/apache/pekko/pull/2724), [PR2865](https://github.com/apache/pekko/pull/2865))
* Changed the pekko-serialization-jackson lz4-java dependency to `at.yawk.lz4:lz4-java`, a fork that has important bug fixes ([PR2537](https://github.com/apache/pekko/pull/2537))

### Bug Fixes

* Replaying a replicated event sourced actor from a snapshot caused a bug ([PR2372](https://github.com/apache/pekko/pull/2372))
* Emit `DeletedDurableState` for deleted durable state objects ([PR2397](https://github.com/apache/pekko/pull/2397))
* Fix close being called in cancel for statefulMap ([PR2365](https://github.com/apache/pekko/pull/2365), [PR2385](https://github.com/apache/pekko/pull/2385))
* Fix recoverWith on a failed stage ([PR2631](https://github.com/apache/pekko/pull/2631), [PR2674](https://github.com/apache/pekko/pull/2674), [PR2712](https://github.com/apache/pekko/pull/2712))
* Fix Source.combine with a single source and type-transforming fan-in strategies ([PR2726](https://github.com/apache/pekko/pull/2726))
* Release lease on shard stop ([PR2738](https://github.com/apache/pekko/pull/2738))
* Fix ProducerController crash on restart with an empty unconfirmed buffer (durable queue) ([#2760](https://github.com/apache/pekko/issues/2760), [PR2762](https://github.com/apache/pekko/pull/2762))
* Handle unexpected stop of the TLS actor gracefully ([PR2801](https://github.com/apache/pekko/pull/2801))
* Fix ByteString1C serialization to use a SerializationProxy ([PR2929](https://github.com/apache/pekko/pull/2929))
* Harden async DNS request id handling ([PR2960](https://github.com/apache/pekko/pull/2960))
* High concurrency issues introduced when switching from `sun.misc.Unsafe` to VarHandles ([PR3007](https://github.com/apache/pekko/pull/3007), [PR3008](https://github.com/apache/pekko/pull/3008))
* MapAsyncPartitioned fixes: ordered stage completion after trailing resumed failures, parallel processing preserving order within a partition and a demand-resume deadlock ([PR3009](https://github.com/apache/pekko/pull/3009), [#2886](https://github.com/apache/pekko/issues/2886), [PR3365](https://github.com/apache/pekko/pull/3365))
* Await in-flight completion on partner termination in SinkRef ([PR3016](https://github.com/apache/pekko/pull/3016))
* Prevent double materialization of SourceRef and SinkRef ([PR3114](https://github.com/apache/pekko/pull/3114))
* Release references to completed stage logics and to the initial interpreter shell in GraphInterpreter to avoid memory retention ([PR3030](https://github.com/apache/pekko/pull/3030), [PR3031](https://github.com/apache/pekko/pull/3031), [#3243](https://github.com/apache/pekko/issues/3243))
* Discard stale ReceiveTimeout after cancelReceiveTimeout to avoid NPE in actor-typed ([PR3086](https://github.com/apache/pekko/pull/3086))
* Clean up stopped FSM transition listeners ([PR3096](https://github.com/apache/pekko/pull/3096))
* Preserve throttler tokens on failed writes ([PR3097](https://github.com/apache/pekko/pull/3097))
* Initialize timer stage deadlines in preStart instead of at construction ([PR3111](https://github.com/apache/pekko/pull/3111))
* Fix the current message reported to supervision in timer stages ([PR3294](https://github.com/apache/pekko/pull/3294))
* Fix close handling in unfoldResource and unfoldResourceAsync (avoid double close and delay close until a pending read completes) ([PR3157](https://github.com/apache/pekko/pull/3157), [PR3172](https://github.com/apache/pekko/pull/3172))
* Classic remoting robustness fixes: ignore stale ACKs, harden EndpointReader against dispatch errors, resume the reader after a failed handoff and buffer messages during passive handoff ([PR3158](https://github.com/apache/pekko/pull/3158), [PR3166](https://github.com/apache/pekko/pull/3166), [PR3173](https://github.com/apache/pekko/pull/3173), [PR3205](https://github.com/apache/pekko/pull/3205), [PR3361](https://github.com/apache/pekko/pull/3361))
* Validate the full UniqueAddress in Artery system message ACK/NACK handling ([PR3175](https://github.com/apache/pekko/pull/3175))
* Bound Artery stream shutdown ([PR3317](https://github.com/apache/pekko/pull/3317))
* Use `Locale.ROOT` for `toLowerCase`/`toUpperCase` calls ([PR3159](https://github.com/apache/pekko/pull/3159))
* Avoid NPE in the outbound TCP stage when upstream finishes before connect ([PR3259](https://github.com/apache/pekko/pull/3259))
* Close unregistered TCP channels on setup failure ([PR3273](https://github.com/apache/pekko/pull/3273))
* Suppress dead-letter logging during a terminating shutdown when disabled ([PR3262](https://github.com/apache/pekko/pull/3262))
* Expand Array arguments in MarkerLoggingAdapter log templates ([PR3263](https://github.com/apache/pekko/pull/3263))
* Deliver stashed timer messages for UnboundedStash and UnrestrictedStash ([PR3264](https://github.com/apache/pekko/pull/3264), [#3265](https://github.com/apache/pekko/issues/3265))
* Return a proper non-cancelled Cancellable from StubbedActorContext.scheduleOnce ([PR3268](https://github.com/apache/pekko/pull/3268))
* Fix deferred TLS output handling on inbound close and abort the TLS transport on session verification failure ([PR3323](https://github.com/apache/pekko/pull/3323), [PR3324](https://github.com/apache/pekko/pull/3324))
* Fix reentrant BatchingExecutor execution ([PR3331](https://github.com/apache/pekko/pull/3331))
* Notify BroadcastHub and PartitionHub consumers on materializer shutdown ([#3345](https://github.com/apache/pekko/issues/3345), [PR3346](https://github.com/apache/pekko/pull/3346), [PR3347](https://github.com/apache/pekko/pull/3347))
* Enforce resizer bounds and minimum pool size for AdjustPoolSize, including in ClusterRouterPool ([#3253](https://github.com/apache/pekko/issues/3253), [PR3364](https://github.com/apache/pekko/pull/3364), [PR3393](https://github.com/apache/pekko/pull/3393))
* Support `java.math.BigInteger`/`BigDecimal` in cluster-metrics ([#3385](https://github.com/apache/pekko/issues/3385), [PR3382](https://github.com/apache/pekko/pull/3382))
* Invoke lifecycle signal handlers during snapshot recovery ([PR3358](https://github.com/apache/pekko/pull/3358))
* Stop PersistentActor by default when the RecoveryCompleted handler fails ([PR3359](https://github.com/apache/pekko/pull/3359))
* Filter messages from the remember-entities store ([PR3411](https://github.com/apache/pekko/pull/3411))
* Preserve StageActor demand across fused streams ([#3459](https://github.com/apache/pekko/issues/3459), [PR3461](https://github.com/apache/pekko/pull/3461))
* Handle post-Java 8 constant pool entries in LineNumbers ([PR3465](https://github.com/apache/pekko/pull/3465))
* Make AbstractPersistentActorWithTimers and AbstractFSMWithStash subclassable from Java when using Scala 3 ([PR3475](https://github.com/apache/pekko/pull/3475))
* Use a Seq rather than a Set to hold TCP magic ByteStrings ([PR3484](https://github.com/apache/pekko/pull/3484))
* Stop cluster sharding entities stuck in passivation after a timeout ([PR3573](https://github.com/apache/pekko/pull/3573))

### Security Hardening

* Pekko Remote TLS: optional hostname verification for classic remoting, warnings when hostname verification is not enabled or when TLS is not used, a new keystore-password config, eager SSLContext initialization in Artery so that misconfiguration fails fast and support for multiple certificates in the rotating keys engine `ca-cert-file` ([PR3164](https://github.com/apache/pekko/pull/3164), [PR3200](https://github.com/apache/pekko/pull/3200), [PR3397](https://github.com/apache/pekko/pull/3397), [PR3398](https://github.com/apache/pekko/pull/3398), [PR3400](https://github.com/apache/pekko/pull/3400), [PR3479](https://github.com/apache/pekko/pull/3479), [PR3528](https://github.com/apache/pekko/pull/3528))
* Jackson `JsonTypeInfo.Id.MINIMAL_CLASS` is now treated as dangerous, like `Id.CLASS` ([PR3304](https://github.com/apache/pekko/pull/3304))
* Resolve, check and parse manifests more carefully in the Jackson serializers ([PR3503](https://github.com/apache/pekko/pull/3503))
* New `max-decompressed-size` limit for Jackson payload decompression and for other compressed payloads (defaults to unlimited for Jackson) ([PR3491](https://github.com/apache/pekko/pull/3491), [PR3502](https://github.com/apache/pekko/pull/3502), [#3504](https://github.com/apache/pekko/issues/3504), [PR3515](https://github.com/apache/pekko/pull/3515), [PR3597](https://github.com/apache/pekko/pull/3597))
* Support `unlimited` for the Jackson stream-read constraints ([PR3522](https://github.com/apache/pekko/pull/3522))
* Bound inbound Artery TCP frame length at framing ([PR3492](https://github.com/apache/pekko/pull/3492), [PR3524](https://github.com/apache/pekko/pull/3524))
* Bound nesting depth when deserializing enclosed payloads ([PR3500](https://github.com/apache/pekko/pull/3500))
* Bound the number of entries in an Artery compression table advertisement ([PR3510](https://github.com/apache/pekko/pull/3510))
* Bound compression-pointer hops in DNS name parsing ([PR3490](https://github.com/apache/pekko/pull/3490))
* Don't resolve HOCON includes in config that arrived in a message ([PR3505](https://github.com/apache/pekko/pull/3505))
* Match actor selection wildcards without backtracking ([PR3506](https://github.com/apache/pekko/pull/3506))
* Reject messages whose parallel repeated protobuf fields disagree in length ([PR3507](https://github.com/apache/pekko/pull/3507))
* Bounds check the lookup table indexes in cluster gossip ([PR3508](https://github.com/apache/pekko/pull/3508))
* Don't dereference a null superclass in the protobuf allow list check ([PR3509](https://github.com/apache/pekko/pull/3509))
* Bound persistence event replay batches ([PR3325](https://github.com/apache/pekko/pull/3325))
* Add `maxTotalBufferSize` to MergeHub for an aggregate queue bound ([PR3392](https://github.com/apache/pekko/pull/3392))

### Ported from Akka

These changes were copied from Akka releases (up to and including Akka 2.8.5) that are now available under the Apache License, version 2.0.

* Unpersistent versions of persistent behaviors for testing, renamed to `PersistenceProbeBehavior` ([PR2456](https://github.com/apache/pekko/pull/2456), [PR2494](https://github.com/apache/pekko/pull/2494))
* Custom stash support in persistence-typed ([PR2433](https://github.com/apache/pekko/pull/2433))
* Asking support in the typed BehaviorTestKit ([PR2450](https://github.com/apache/pekko/pull/2450), [PR2453](https://github.com/apache/pekko/pull/2453))
* ShardedDaemonProcess: throttle keep-alive messages and limit them to a subset of nodes ([PR2734](https://github.com/apache/pekko/pull/2734))
* Distributed Data: wildcard prefix subscriptions in the Replicator ([PR2735](https://github.com/apache/pekko/pull/2735))
* Include roundtrip latency and sequence number in the heartbeat debug log ([PR2736](https://github.com/apache/pekko/pull/2736))
* Optimize PropsAdapter, typed actor context and system adapter ([PR2743](https://github.com/apache/pekko/pull/2743), [PR2750](https://github.com/apache/pekko/pull/2750))
* Auto-select SRV lookup for the name lookup if the entry looks like an SRV name ([PR2745](https://github.com/apache/pekko/pull/2745))
* Run the DurableStateBehavior delete effect asynchronously like persist ([PR2752](https://github.com/apache/pekko/pull/2752))
* Use ClusterShuttingDown in the Distributed Data Replicator ([PR2765](https://github.com/apache/pekko/pull/2765))
* Add ScheduledClock to reduce nanoTime overhead in recency-based passivation ([PR2766](https://github.com/apache/pekko/pull/2766))
* Snapshot retention: only-one-snapshot optimization and only one retention cycle in progress at a time ([PR2767](https://github.com/apache/pekko/pull/2767), [PR2797](https://github.com/apache/pekko/pull/2797))
* Improve the cluster sharding StopShards coordinator command and fix the coordinator role ([PR2783](https://github.com/apache/pekko/pull/2783), [PR2918](https://github.com/apache/pekko/pull/2918))
* Disable ClusterShardingHealthCheck after a configured duration post member-up ([PR2785](https://github.com/apache/pekko/pull/2785))
* Remember entities throttling per region instead of per shard and improved DData remember entities logging and consistency ([PR2786](https://github.com/apache/pekko/pull/2786), [PR2799](https://github.com/apache/pekko/pull/2799))
* Improve error message for actorOf on a typed ActorSystem ([PR2798](https://github.com/apache/pekko/pull/2798))
* Fine-grained stream error logging control ([PR2805](https://github.com/apache/pekko/pull/2805))
* Add filtered, source and tags fields to typed EventEnvelope, EventsByPersistenceIdTyped queries and `withTaggerForState` ([PR2807](https://github.com/apache/pekko/pull/2807), [PR2920](https://github.com/apache/pekko/pull/2920))
* Support for external transport of replicated events in Replicated Event Sourcing ([PR2808](https://github.com/apache/pekko/pull/2808))
* DnsResolutionActor and in-flight request deduplication ([PR2919](https://github.com/apache/pekko/pull/2919))
* Initialize the DistributedData extension from ExternalShardAllocationStrategy ([PR2923](https://github.com/apache/pekko/pull/2923))
* Make it possible to define the cluster `appVersion` later ([PR2947](https://github.com/apache/pekko/pull/2947))
* Support ActorSystem/ClassicActorSystemProvider serializer constructors and improve the error message ([PR2948](https://github.com/apache/pekko/pull/2948))
* Runtime plugin configuration for DurableState ([PR3058](https://github.com/apache/pekko/pull/3058))
* Consistent-hashing shard allocation strategy ([PR3270](https://github.com/apache/pekko/pull/3270))
* Include the exception type in StatusReply toString when not text ([PR3271](https://github.com/apache/pekko/pull/3271))
* FilteredPayload placeholder for filtered journal events ([PR3275](https://github.com/apache/pekko/pull/3275))
* Scalable fan-out persistence query abstraction (`EventsBySliceFirehoseQuery`) and `EventsBySliceStartingFromSnapshotsQuery` ([PR3277](https://github.com/apache/pekko/pull/3277))
* Include sourceThread in the typed actor MDC ([PR3357](https://github.com/apache/pekko/pull/3357))
* Other Akka 2.8.4 changes ([PR3432](https://github.com/apache/pekko/pull/3432))

### Additions

* Add `ActorSystem.terminateAndAwait` and `ActorSystem.close` ([PR2096](https://github.com/apache/pekko/pull/2096), [PR2486](https://github.com/apache/pekko/pull/2486))
* Add `UntypedAbstractActorWithStash`, `UntypedAbstractActorWithUnboundedStash` and `UntypedAbstractActorWithUnrestrictedStash` ([PR2097](https://github.com/apache/pekko/pull/2097))
* Add actor-typed Java DSL `AbstractMatchingBehavior` ([#2108](https://github.com/apache/pekko/issues/2108), [PR2379](https://github.com/apache/pekko/pull/2379))
* Add logging variants for the Java DSL stash and timer actor base classes ([#2520](https://github.com/apache/pekko/issues/2520), [PR3387](https://github.com/apache/pekko/pull/3387))
* Add the timeout duration to the `ReceiveTimeout` message ([PR3399](https://github.com/apache/pekko/pull/3399))
* Add `andThen` and `compose` to `pekko.japi.function.Function` ([#2144](https://github.com/apache/pekko/issues/2144), [PR2147](https://github.com/apache/pekko/pull/2147))
* Add a throwing boolean supplier, `sneakyThrow` and `OptionalUtil` Java utilities ([PR2199](https://github.com/apache/pekko/pull/2199), [PR2218](https://github.com/apache/pekko/pull/2218), [PR2464](https://github.com/apache/pekko/pull/2464))
* Add `CompletionStages` utility and make `timeoutCompletionStage`/`afterCompletionStage` accept Java Duration ([PR2060](https://github.com/apache/pekko/pull/2060), [PR2063](https://github.com/apache/pekko/pull/2063), [PR2067](https://github.com/apache/pekko/pull/2067))
* Add `StatusReply.fromTry` and `StatusReply.fromCallable` ([PR2810](https://github.com/apache/pekko/pull/2810), [PR2812](https://github.com/apache/pekko/pull/2812))
* Add `JournalPersistFailed` and `JournalPersistRejected` signals ([PR1961](https://github.com/apache/pekko/pull/1961))
* Add an AsyncWriteJournal option for disabling the Resequencer ([PR2027](https://github.com/apache/pekko/pull/2027))
* Add `DurableStateBehaviorTestKit` ([PR3360](https://github.com/apache/pekko/pull/3360))
* Java DSL TestKit `shutdownActorSystem` that takes Java Duration params ([#2226](https://github.com/apache/pekko/issues/2226), [PR2277](https://github.com/apache/pekko/pull/2277))
* Add virtualize support for thread-pool-executor (including the blocking IO dispatcher) and support setting the starting number of virtual threads ([#2163](https://github.com/apache/pekko/issues/2163), [PR2169](https://github.com/apache/pekko/pull/2169), [PR2242](https://github.com/apache/pekko/pull/2242))
* Make ForkJoinPool `minimum-runnable` configurable and auto-tune it on JDK 25+ ([PR2871](https://github.com/apache/pekko/pull/2871), [PR2890](https://github.com/apache/pekko/pull/2890))
* Support the `SO_REUSEPORT` socket option ([PR2915](https://github.com/apache/pekko/pull/2915))
* Add `ByteString.endsWith`, `indexOf` with from and to, and make `ByteString.copyToBuffer(buffer, offset)` public ([PR2271](https://github.com/apache/pekko/pull/2271), [PR2862](https://github.com/apache/pekko/pull/2862), [PR2946](https://github.com/apache/pekko/pull/2946))
* Add OSGi headers to pekko-pki and pekko-cluster-typed and compute OSGi imports for Pekko packages dynamically ([PR2107](https://github.com/apache/pekko/pull/2107), [PR2112](https://github.com/apache/pekko/pull/2112), [PR2313](https://github.com/apache/pekko/pull/2313))
* Add Scala 3.8 and 3.9 compatibility (Pekko is still published using Scala 3.3 LTS) ([PR3072](https://github.com/apache/pekko/pull/3072), [PR3073](https://github.com/apache/pekko/pull/3073), [PR3074](https://github.com/apache/pekko/pull/3074), [PR3295](https://github.com/apache/pekko/pull/3295), [PR3303](https://github.com/apache/pekko/pull/3303))

The Stream API has been updated to add some extra operators.

* Add `Sink.source` and `materializeIntoSource` operators ([#2222](https://github.com/apache/pekko/issues/2222), [PR2250](https://github.com/apache/pekko/pull/2250), [PR1831](https://github.com/apache/pekko/pull/1831))
* Add `Sink#count` operator ([#2182](https://github.com/apache/pekko/issues/2182), [PR2244](https://github.com/apache/pekko/pull/2244))
* Add `onErrorResume` (including Java DSL, SubFlow and SubSource variants) and more recover operators for the Java DSL ([#2116](https://github.com/apache/pekko/issues/2116), [PR2120](https://github.com/apache/pekko/pull/2120), [PR2336](https://github.com/apache/pekko/pull/2336), [PR2337](https://github.com/apache/pekko/pull/2337))
* Add `onErrorContinue` operator ([#2159](https://github.com/apache/pekko/issues/2159), [PR2322](https://github.com/apache/pekko/pull/2322))
* Add `doOnFirst` operator ([#2183](https://github.com/apache/pekko/issues/2183), [PR2363](https://github.com/apache/pekko/pull/2363))
* Add `doOnCancel` operator ([#2374](https://github.com/apache/pekko/issues/2374), [PR2375](https://github.com/apache/pekko/pull/2375))
* Add `gzipDecompress` operator, replacing the deprecated `gunzip` (which has been removed) ([PR2406](https://github.com/apache/pekko/pull/2406))
* Add `Source.fromOption` and `mapOption` operators ([#2408](https://github.com/apache/pekko/issues/2408), [#2403](https://github.com/apache/pekko/issues/2403), [PR2413](https://github.com/apache/pekko/pull/2413), [PR2414](https://github.com/apache/pekko/pull/2414))
* Add `Source.items` and an array based `Source.apply` ([#2416](https://github.com/apache/pekko/issues/2416), [PR2424](https://github.com/apache/pekko/pull/2424), [PR2428](https://github.com/apache/pekko/pull/2428), [PR2429](https://github.com/apache/pekko/pull/2429))
* Add `withContext` operators ([PR3199](https://github.com/apache/pekko/pull/3199))
* Add an `alsoTo` overload with configurable cancellation propagation ([PR3127](https://github.com/apache/pekko/pull/3127))
* Add supervision strategy support to more operators that accept user functions (`groupedWeightedWithin`, `batch`, `expand` and `extrapolate`) and log supervision Resume/Restart in mapAsync operators ([#3110](https://github.com/apache/pekko/issues/3110), [PR3124](https://github.com/apache/pekko/pull/3124), [PR3180](https://github.com/apache/pekko/pull/3180), [PR3184](https://github.com/apache/pekko/pull/3184), [PR3185](https://github.com/apache/pekko/pull/3185))
* Add an opt-in GraphStage based TLS engine (the actor based implementation remains the default) ([PR2878](https://github.com/apache/pekko/pull/2878))

### Changes

* Make `SubFlow` and `SubSource` final classes and switch the type parameter order of `UnfoldResourceSource` and `UnfoldResourceSourceAsync` ([PR615](https://github.com/apache/pekko/pull/615), [PR616](https://github.com/apache/pekko/pull/616), [PR619](https://github.com/apache/pekko/pull/619))
* Remove the unnecessary empty parameter list from `watchTermination` ([#2376](https://github.com/apache/pekko/issues/2376), [PR2378](https://github.com/apache/pekko/pull/2378))
* Change `Futures#find` and other `Futures` methods to return Java `CompletionStage` ([#2008](https://github.com/apache/pekko/issues/2008), [PR2009](https://github.com/apache/pekko/pull/2009))
* Stream Java DSL now uses `pekko.japi.function` types throughout ([#2118](https://github.com/apache/pekko/issues/2118), [PR2143](https://github.com/apache/pekko/pull/2143))
* Fix the Java DSL `SourceWithContext`/`FlowWithContext` shape to use `pekko.japi.Pair` ([#2510](https://github.com/apache/pekko/issues/2510), [PR3388](https://github.com/apache/pekko/pull/3388))
* Replace `scala.Tuple2` with `pekko.japi.Pair` in `FSMTransitionHandlerBuilder` ([PR3378](https://github.com/apache/pekko/pull/3378))
* Replace `immutable.Traversable` in the public io and stream APIs ([PR3467](https://github.com/apache/pekko/pull/3467), [PR3468](https://github.com/apache/pekko/pull/3468))
* Remove `ExecutorServiceFactoryProvider` from `ThreadPoolConfig` ([#2171](https://github.com/apache/pekko/issues/2171), [PR2175](https://github.com/apache/pekko/pull/2175))
* Replace CountMinSketch with FastFrequencySketch in Artery compression ([PR3023](https://github.com/apache/pekko/pull/3023))
* Replace the actor based fanout publisher runtime (`Sink.asPublisher(fanout = true)`) with a GraphStage bridge ([PR2874](https://github.com/apache/pekko/pull/2874))
* Remove the deprecated annotation from `recoverWith` and undeprecate the `getFirst`/`getFirstAttribute` methods ([PR2119](https://github.com/apache/pekko/pull/2119), [PR3404](https://github.com/apache/pekko/pull/3404))
* Mark public sealed traits and abstract classes with `@DoNotInherit` ([#1270](https://github.com/apache/pekko/issues/1270), [PR3386](https://github.com/apache/pekko/pull/3386))
* Remove the Akka 2.6.4 rolling-migration manifests from ClusterMessageSerializer ([PR3516](https://github.com/apache/pekko/pull/3516))
* ClusterShardingSettings: remove backward-compat passivation code ([PR3026](https://github.com/apache/pekko/pull/3026))
* Remove the `ssl-config` dependency ([#2125](https://github.com/apache/pekko/issues/2125), [PR2127](https://github.com/apache/pekko/pull/2127), [PR2359](https://github.com/apache/pekko/pull/2359))
* Remove usage of `SecurityManager` ([#1970](https://github.com/apache/pekko/issues/1970), [PR2106](https://github.com/apache/pekko/pull/2106))
* Use the better ByteBuffer cleaner available in Java 9+ ([PR2020](https://github.com/apache/pekko/pull/2020), [PR2031](https://github.com/apache/pekko/pull/2031))
* Code modernisation to use Java 17 language features and APIs (pattern matching instanceof, switch expressions, records, sealed types, collection factories, lambdas instead of anonymous inner classes) ([#2083](https://github.com/apache/pekko/issues/2083), [#2201](https://github.com/apache/pekko/issues/2201), [PR2088](https://github.com/apache/pekko/pull/2088), [PR3188](https://github.com/apache/pekko/pull/3188), [PR3190](https://github.com/apache/pekko/pull/3190), [PR3191](https://github.com/apache/pekko/pull/3191), [PR3192](https://github.com/apache/pekko/pull/3192), [PR3193](https://github.com/apache/pekko/pull/3193), [PR3194](https://github.com/apache/pekko/pull/3194), [PR3379](https://github.com/apache/pekko/pull/3379))
* Code modernisation for Scala 2.13 and Scala 3, using the standard `scala.jdk` converters instead of compat libraries ([#2126](https://github.com/apache/pekko/issues/2126), [PR2205](https://github.com/apache/pekko/pull/2205), [PR2206](https://github.com/apache/pekko/pull/2206), [PR2236](https://github.com/apache/pekko/pull/2236), [PR3539](https://github.com/apache/pekko/pull/3539))
* Use `-Yfuture-lazy-vals` for Scala 3 builds ([PR3059](https://github.com/apache/pekko/pull/3059))
* Fix many compiler, Scaladoc and Javadoc warnings ([PR3283](https://github.com/apache/pekko/pull/3283), [PR3284](https://github.com/apache/pekko/pull/3284), [PR3285](https://github.com/apache/pekko/pull/3285), [PR3297](https://github.com/apache/pekko/pull/3297), [PR3330](https://github.com/apache/pekko/pull/3330))
* Regenerated the protobuf source code using protoc 4.36 ([PR2036](https://github.com/apache/pekko/pull/2036), [PR2335](https://github.com/apache/pekko/pull/2335), [PR3514](https://github.com/apache/pekko/pull/3514))
* Validate legal files in packaged jars ([PR3328](https://github.com/apache/pekko/pull/3328))
* Many documentation improvements, including a list of configuration changes in the migration guide, docs for the jackson3 module, the JUnit Jupiter test kit, custom Durable State store plugins and stream operator reference pages for previously undocumented operators ([PR2391](https://github.com/apache/pekko/pull/2391), [PR2468](https://github.com/apache/pekko/pull/2468), [PR3274](https://github.com/apache/pekko/pull/3274), [PR3513](https://github.com/apache/pekko/pull/3513), [PR3583](https://github.com/apache/pekko/pull/3583), [PR3585](https://github.com/apache/pekko/pull/3585), [#3589](https://github.com/apache/pekko/issues/3589), [PR3590](https://github.com/apache/pekko/pull/3590))

### Performance

* SIMD within a register (SWAR) and VarHandle based ByteString search (`indexOf`, `lastIndexOf`, `indexOfSlice`, `contains`) ([#1264](https://github.com/apache/pekko/issues/1264), [#2150](https://github.com/apache/pekko/issues/2150), [PR2148](https://github.com/apache/pekko/pull/2148), [PR2306](https://github.com/apache/pekko/pull/2306), [PR2309](https://github.com/apache/pekko/pull/2309), [PR2838](https://github.com/apache/pekko/pull/2838), [PR2839](https://github.com/apache/pekko/pull/2839), [PR2863](https://github.com/apache/pekko/pull/2863))
* Other ByteString improvements: faster equality, concatenation, slicing, fragment lookup, `ByteStringBuilder.result` and multi-byte reads ([PR2847](https://github.com/apache/pekko/pull/2847), [PR2924](https://github.com/apache/pekko/pull/2924), [PR3427](https://github.com/apache/pekko/pull/3427), [PR3428](https://github.com/apache/pekko/pull/3428), [PR3463](https://github.com/apache/pekko/pull/3463), [PR3471](https://github.com/apache/pekko/pull/3471), [PR3526](https://github.com/apache/pekko/pull/3526))
* Faster `UnsynchronizedByteArrayInputStream` usage ([PR2300](https://github.com/apache/pekko/pull/2300), [PR3541](https://github.com/apache/pekko/pull/3541))
* Batch elements across internal async stream boundaries ([PR3288](https://github.com/apache/pekko/pull/3288), [PR3296](https://github.com/apache/pekko/pull/3296), [#3459](https://github.com/apache/pekko/issues/3459), [PR3461](https://github.com/apache/pekko/pull/3461))
* Optimize `Source.from` iterable, `Source.future`, `Source.futureSource` and `Source.apply` for Seq ([PR2556](https://github.com/apache/pekko/pull/2556), [PR2560](https://github.com/apache/pekko/pull/2560), [PR2562](https://github.com/apache/pekko/pull/2562))
* Avoid substream materialization for value-presented sources in FlattenMerge and concat ([PR2977](https://github.com/apache/pekko/pull/2977), [PR2978](https://github.com/apache/pekko/pull/2978))
* GraphInterpreter hot path optimizations ([PR2986](https://github.com/apache/pekko/pull/2986), [PR3119](https://github.com/apache/pekko/pull/3119), [PR3373](https://github.com/apache/pekko/pull/3373))
* Stream materializer wiring and stage actor optimizations ([PR3035](https://github.com/apache/pekko/pull/3035), [PR3062](https://github.com/apache/pekko/pull/3062), [PR3063](https://github.com/apache/pekko/pull/3063))
* Optimize `Source.actorRef` with a direct-push fast path ([PR3091](https://github.com/apache/pekko/pull/3091))
* Avoid Holder allocation in ordered mapAsync for already-completed futures ([PR3018](https://github.com/apache/pekko/pull/3018))
* Use an ArrayList in BroadcastHub ([PR2262](https://github.com/apache/pekko/pull/2262))
* Reduce receive-timeout state allocations and avoid type pollution ([PR3332](https://github.com/apache/pekko/pull/3332), [PR3334](https://github.com/apache/pekko/pull/3334))
* Validate actor path elements without `String.charAt` ([PR3542](https://github.com/apache/pekko/pull/3542))

### Removed

Much of the code that was deprecated in Pekko 1.x (and in Akka before that) has been removed. At a high level:

* Typed Actors (the classic `TypedActor` API, not Pekko Typed) ([#571](https://github.com/apache/pekko/issues/571), [PR1969](https://github.com/apache/pekko/pull/1969))
* `ActorMaterializer` class and deprecated materializer, `ActorMaterializerSettings`, `IOSettings` and `StreamRefSettings` factory methods ([PR2011](https://github.com/apache/pekko/pull/2011), [PR2137](https://github.com/apache/pekko/pull/2137), [PR3293](https://github.com/apache/pekko/pull/3293))
* Deprecated stream operators and methods, including the Future based operators, stream converters, `OverflowStrategy.dropNew`, `SubstreamCancelStrategy`, the old `splitWhen`/`splitAfter` overloads, `GraphStage.onDownstreamFinish()` and the stream testkit `probe` methods ([PR1958](https://github.com/apache/pekko/pull/1958), [PR1996](https://github.com/apache/pekko/pull/1996), [PR2006](https://github.com/apache/pekko/pull/2006), [PR2012](https://github.com/apache/pekko/pull/2012), [PR2017](https://github.com/apache/pekko/pull/2017), [PR2073](https://github.com/apache/pekko/pull/2073), [PR2129](https://github.com/apache/pekko/pull/2129), [PR2146](https://github.com/apache/pekko/pull/2146), [PR2440](https://github.com/apache/pekko/pull/2440), [PR3292](https://github.com/apache/pekko/pull/3292))
* Deprecated actor, scheduler, testkit, remote, cluster, cluster-sharding (including old backoff classes and settings), distributed-data, persistence and DNS code ([PR1945](https://github.com/apache/pekko/pull/1945), [PR1959](https://github.com/apache/pekko/pull/1959), [PR1965](https://github.com/apache/pekko/pull/1965), [PR1966](https://github.com/apache/pekko/pull/1966), [PR1969](https://github.com/apache/pekko/pull/1969), [PR1981](https://github.com/apache/pekko/pull/1981), [PR1983](https://github.com/apache/pekko/pull/1983), [PR2018](https://github.com/apache/pekko/pull/2018), [PR2023](https://github.com/apache/pekko/pull/2023), [PR2037](https://github.com/apache/pekko/pull/2037), [PR3289](https://github.com/apache/pekko/pull/3289))
* `pekko.japi.Function` and related classes, `FI`, `JAPI` and `pekko.japi.Option` ([PR2007](https://github.com/apache/pekko/pull/2007), [PR2078](https://github.com/apache/pekko/pull/2078), [PR2079](https://github.com/apache/pekko/pull/2079), [PR2193](https://github.com/apache/pekko/pull/2193))
* Scala version compatibility classes in `pekko.util` (such as `ccompat`, `FutureConverters`, `OptionConverters`, `ExecutionContexts.parasitic`) as Pekko now uses the Scala 2.13 standard library equivalents ([PR2207](https://github.com/apache/pekko/pull/2207), [PR2208](https://github.com/apache/pekko/pull/2208), [PR2212](https://github.com/apache/pekko/pull/2212), [PR2234](https://github.com/apache/pekko/pull/2234), [PR2582](https://github.com/apache/pekko/pull/2582))
* Internal and unused code such as `ReentrantGuard`, `AbruptIOTerminationException`, `ActorProcessor` and the classic remoting `disassociate` methods ([#2160](https://github.com/apache/pekko/issues/2160), [PR2065](https://github.com/apache/pekko/pull/2065), [PR2072](https://github.com/apache/pekko/pull/2072), [PR2074](https://github.com/apache/pekko/pull/2074), [PR2161](https://github.com/apache/pekko/pull/2161), [PR3027](https://github.com/apache/pekko/pull/3027))
* The `ssl-config` configuration sections ([PR2359](https://github.com/apache/pekko/pull/2359))

### Deprecations

Some additional APIs have been deprecated and will be removed in a future release.

* `org.apache.pekko.dispatch.Futures` ([#1417](https://github.com/apache/pekko/issues/1417), [PR3377](https://github.com/apache/pekko/pull/3377))
* `Patterns.timeout` ([PR2050](https://github.com/apache/pekko/pull/2050))
* `Source.future` in the Java DSL ([PR2593](https://github.com/apache/pekko/pull/2593))
* `BalancingDispatcherConfigurator`, which is now also marked as internal API ([#3384](https://github.com/apache/pekko/issues/3384), [PR3391](https://github.com/apache/pekko/pull/3391))
* The `pekko.persistence.dispatchers` configuration ([PR2482](https://github.com/apache/pekko/pull/2482))

### Dependency Changes

* Java 17 is the minimum supported version
* Scala 2.13.18 and 3.3.8
* netty 4.2.19.Final
* jackson 2.22.3 (pekko-serialization-jackson) and jackson 3.2.3 (pekko-serialization-jackson3)
* aeron 1.53.3 and agrona 2.6.1
* protobuf-java 4.36.2
* lightbend/config 1.4.9
* slf4j 2.0.20
* lz4-java 1.12.0 (`at.yawk.lz4:lz4-java`)
* junit-jupiter 6.1.3
* The `ssl-config` dependency has been removed

### Known Issues

* MergeLatest does not backpressure upstreams and buffers without bound ([#3592](https://github.com/apache/pekko/issues/3592)).
