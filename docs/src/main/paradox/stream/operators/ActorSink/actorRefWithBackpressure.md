# ActorSink.actorRefWithBackpressure

Sends the elements of the stream to the given @java[`ActorRef<T>`]@scala[`ActorRef[T]`] of the new actors API with backpressure, to be able to signal demand when the actor is ready to receive more elements.

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

@apidoc[ActorSink.actorRefWithBackpressure](ActorSink$) { scala="#actorRefWithBackpressure[T,M,A](ref:org.apache.pekko.actor.typed.ActorRef[M],messageAdapter:(org.apache.pekko.actor.typed.ActorRef[A],T)=&gt;M,onInitMessage:org.apache.pekko.actor.typed.ActorRef[A]=&gt;M,ackMessage:A,onCompleteMessage:M,onFailureMessage:Throwable=&gt;M):org.apache.pekko.stream.scaladsl.Sink[T,org.apache.pekko.NotUsed]" java="#actorRefWithBackpressure(org.apache.pekko.actor.typed.ActorRef,org.apache.pekko.japi.function.Function2,org.apache.pekko.japi.function.Function,java.lang.Object,java.lang.Object,org.apache.pekko.japi.function.Function)" }

## Description

Sends the elements of the stream to the given @java[`ActorRef<T>`]@scala[`ActorRef[T]`] with backpressure, to be able to signal demand when the actor is ready to receive more elements.
There is also a variant without a concrete acknowledge message accepting any message as such.

See also:

* @ref[`ActorSink.actorRef`](../ActorSink/actorRefWithBackpressure.md) Send elements to an actor of the new actors API, without considering backpressure
* @ref[`Sink.actorRef`](../Sink/actorRef.md) Send elements to an actor of the classic actors API, without considering backpressure
* @ref[`Sink.actorRefWithBackpressue`](../Sink/actorRefWithBackpressure.md) The corresponding operator for the classic actors API

## Examples

Scala
:  @@snip [ActorSourceSinkExample.scala](/akka-stream-typed/src/test/scala/docs/org/apache/pekko/stream/typed/ActorSourceSinkExample.scala) { #actor-sink-ref-with-backpressure }

Java
:  @@snip [ActorSinkWithAckExample.java](/akka-stream-typed/src/test/java/docs/org/apache/pekko/stream/typed/ActorSinkWithAckExample.java) { #actor-sink-ref-with-backpressure }

## Reactive Streams semantics

@@@div { .callout }

**cancels** when the actor terminates

**backpressures** when the actor acknowledgement has not arrived

@@@
