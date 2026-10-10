# Sink.watchTermination

Wraps a sink so that in addition to the original materialized value a @scala[`Future[Done]`] @java[`CompletionStage<Done>`] is materialized that only completes after the wrapped sink has fully terminated, including its `postStop` lifecycle hook.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.watchTermination](Sink) { scala="#watchTermination[Mat2](matF:(Mat,scala.concurrent.Future[org.apache.pekko.Done])=&gt;Mat2):org.apache.pekko.stream.scaladsl.Sink[In,Mat2]" java="#watchTermination(org.apache.pekko.japi.function.Function2)" }


## Description

Wraps a sink so that in addition to the original materialized value a @scala[`Future[Done]`] @java[`CompletionStage<Done>`] is materialized
that completes when the wrapped sink has fully terminated: it completes with success after the wrapped sink's `postStop`
lifecycle hook has run, or fails with the upstream failure when the stream failed.

This differs from @ref[watchTermination](../Source-or-Flow/watchTermination.md), which is placed *before* the sink and
therefore only signals when the upstream of the sink has terminated. Because `Sink.watchTermination` wraps the sink
itself, the materialized @scala[`Future`] @java[`CompletionStage`] can be used to wait for any cleanup or final
commits the sink performs in `postStop`, for example a file sink closing the file it was writing to.

Supports sinks built from GraphStages, including composite sinks such as `Sink.foreach`, `Sink.fold`,
`Sink.combine` and GraphDSL graphs. The future completes only after every stage in the wrapped graph
has run its `postStop` and internal cleanup, including stages in separate async islands.
Sinks containing other module types, such as `Sink.fromSubscriber`, throw an
@scala[`IllegalArgumentException`] @java[`IllegalArgumentException`].

The future fails if a stage fails, including an exception from `postStop`, or if the stream is abruptly
terminated. Cancellation with a non-failure cause completes it successfully; cancellation with
a failure cause fails it. If several stages fail,
one of the observed failures is reported after all stages have stopped.
An observed failure is reported even if another stage in the wrapped graph recovers from it.

Only stages already present in the wrapped graph are observed. Sinks created later by `Sink.lazySink`,
`Sink.fromMaterializer`, or operators that materialize substreams are not observed, even when they
run in the same interpreter actor. For those operators, the future only awaits the outer stage's cleanup.
To observe a dynamically created sink, apply `watchTermination` to that sink inside the factory.
Independently materialized streams and asynchronous work started by a lifecycle hook are not awaited
after that hook returns.

Stage cleanup also runs if `postStop` throws, for watched and unwatched stages alike. This ensures
that stage actors, timers, pending async callback feedback and substreams are cleaned up on that path.

## Examples

Scala
:   @@snip [WatchTermination.scala](/docs/src/test/scala/docs/stream/operators/sink/WatchTermination.scala) { #watchTermination }

Java
:   @@snip [WatchTermination.java](/docs/src/test/java/jdocs/stream/operators/sink/WatchTermination.java) { #watchTermination }

## Reactive Streams semantics

@@@div { .callout }

**backpressures** when the wrapped sink backpressures

**cancels** when the wrapped sink cancels

@@@
