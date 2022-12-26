# watch

Watch a specific `ActorRef` and signal a failure downstream once the actor terminates.

@ref[Actor interop operators](../index.md#actor-interop-operators)

## Signature

@apidoc[Source.watch](Source) { scala="#watch(ref:org.apache.pekko.actor.ActorRef):FlowOps.this.Repr[Out]" java="#watch(org.apache.pekko.actor.ActorRef)" }
@apidoc[Flow.watch](Flow) { scala="#watch(ref:org.apache.pekko.actor.ActorRef):FlowOps.this.Repr[Out]" java="#watch(org.apache.pekko.actor.ActorRef)" }

## Description

Watch a specific `ActorRef` and signal a failure downstream once the actor terminates.
The signaled failure will be an @java[@javadoc:[WatchedActorTerminatedException](pekko.stream.WatchedActorTerminatedException)]
@scala[@scaladoc[WatchedActorTerminatedException](pekko.stream.WatchedActorTerminatedException)].

## Example

An `ActorRef` can be can be watched and the stream will fail with `WatchedActorTerminatedException` when the
actor terminates. 

Scala
:   @@snip [Watch.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Watch.scala) { #watch }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #watch }


## Reactive Streams semantics

@@@div { .callout }

**emits** when upstream emits

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@

