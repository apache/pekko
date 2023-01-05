# ActorSource.actorRefWithBackpressure

Materialize an @java[`ActorRef<T>`]@scala[`ActorRef[T]`] of the new actors API; sending messages to it will emit them on the stream. The source acknowledges reception after emitting a message, to provide back pressure from the source.

@ref[Actor interop operators](../index.md#actor-interop-operators)

## Dependency

This operator is included in:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=akka-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group="org.apache.pekko"
  artifact="akka-stream-typed_$scala.binary.version$"
  version=PekkoVersion
}

## Signature

@apidoc[ActorSource.actorRefWithBackpressure](ActorSource$) { scala="#actorRefWithBackpressure[T,Ack](ackTo:org.apache.pekko.actor.typed.ActorRef[Ack],ackMessage:Ack,completionMatcher:PartialFunction[T,org.apache.pekko.stream.CompletionStrategy],failureMatcher:PartialFunction[T,Throwable]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.actor.typed.ActorRef[T]]" java="#actorRefWithBackpressure(org.apache.pekko.actor.typed.ActorRef,java.lang.Object,org.apache.pekko.japi.function.Function,org.apache.pekko.japi.function.Function)" }

## Description

Materialize an @java[`ActorRef<T>`]@scala[`ActorRef[T]`], sending messages to it will emit them on the stream. The actor responds with the provided ack message
once the element could be emitted allowing for backpressure from the source. Sending another message before the previous one has been acknowledged will fail the stream.

See also:

* @ref[ActorSource.actorRef](actorRef.md) This operator, but without backpressure control
* @ref[Source.actorRef](../Source/actorRef.md) This operator, but without backpressure control for the classic actors API
* @ref[Source.actorRefWithBackpressure](../Source/actorRefWithBackpressure.md) This operator for the classic actors API
* @ref[Source.queue](../Source/queue.md) Materialize a `SourceQueue` onto which elements can be pushed for emitting from the source

## Example

With `actorRefWithBackpressure` two actors get into play: 

1. An actor that is materialized when the stream runs. It feeds the stream.
2. An actor provided by the user. It gets the ack signal when an element is emitted into the stream.

For the ack signal we create an @scala[`Emitted` object]@java[empty `Emitted` class].

For "feeding" the stream we use the `Event` @scala[trait]@java[interface].

In this example we create the stream in an actor which itself reacts on the demand of the stream and sends more messages.


Scala
:  @@snip [ActorSourceSinkExample.scala](/stream-typed/src/test/scala/docs/org/apache/pekko/stream/typed/ActorSourceSinkExample.scala) { #actor-source-with-backpressure }

Java
:  @@snip [snip](/stream-typed/src/test/java/docs/org/apache/pekko/stream/typed/ActorSourceWithBackpressureExample.java) { #sample }

## Reactive Streams semantics

@@@div { .callout }

**emits** when a message is sent to the materialized @scala[`ActorRef[T]`]@java[`ActorRef<T>`] it is emitted as soon as there is demand from downstream

**completes** when the passed completion matcher returns a `CompletionStrategy`

@@@
