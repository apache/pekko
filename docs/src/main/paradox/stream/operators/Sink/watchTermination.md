# Sink.watchTermination

Wraps a sink so that in addition to the original materialized value a @scala[`Future[Done]`] @java[`CompletionStage<Done>`] is materialized that only completes after the wrapped sink has fully terminated, including its `postStop` lifecycle hook.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.watchTermination](Sink) { scala="#watchTermination[Mat2]()(matF:(Mat,scala.concurrent.Future[org.apache.pekko.Done])=&gt;Mat2):org.apache.pekko.stream.scaladsl.Sink[In,Mat2]" java="#watchTermination(org.apache.pekko.japi.function.Function2)" }


## Description

Wraps a sink so that in addition to the original materialized value a @scala[`Future[Done]`] @java[`CompletionStage<Done>`] is materialized
that completes when the wrapped sink has fully terminated: it completes with success after the wrapped sink's `postStop`
lifecycle hook has run, or fails with the upstream failure when the stream failed.

This differs from @ref[watchTermination](../Source-or-Flow/watchTermination.md), which is placed *before* the sink and
therefore only signals when the upstream of the sink has terminated. Because `Sink.watchTermination` wraps the sink
itself, the materialized @scala[`Future`] @java[`CompletionStage`] can be used to wait for any cleanup or final
commits the sink performs in `postStop`, for example a file sink closing the file it was writing to.

Only sinks that consist of a single `GraphStage` are supported, for example `Sink.ignore`, `Sink.head`,
`Sink.queue`, `Sink.actorRef` or sinks created from custom graph stages. Composite sinks consisting of
multiple stages — such as `Sink.foreach`, `Sink.fold`, or sinks created with `Sink.combine` or `GraphDSL` —
are not supported and throw an @scala[`IllegalArgumentException`] @java[`IllegalArgumentException`].

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
