# Source.actorRefWithBackpressure

Materialize an `ActorRef` of the classic actors API; sending messages to it will emit them on the stream. The source acknowledges reception after emitting a message, to provide back pressure from the source.

@ref[Actor interop operators](../index.md#actor-interop-operators)

## Signature

@apidoc[Source.actorRefWithBackpressure](Source$) { scala="#actorRefWithBackpressure[T](ackMessage:Any,completionMatcher:PartialFunction[Any,org.apache.pekko.stream.CompletionStrategy],failureMatcher:PartialFunction[Any,Throwable]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.actor.ActorRef]" java="#actorRefWithBackpressure(java.lang.Object,org.apache.pekko.japi.function.Function,org.apache.pekko.japi.function.Function)" }

## Description

Materialize an `ActorRef`, sending messages to it will emit them on the stream. The actor responds with the provided ack message
once the element could be emitted allowing for backpressure from the source. Sending another message before the previous one has been acknowledged will fail the stream.

See also:

* @ref[Source.actorRef](../Source/actorRef.md) This operator without backpressure control
* @ref[ActorSource.actorRef](../ActorSource/actorRef.md) The operator for the new actors API without backpressure control
* @ref[ActorSource.actorRefWithBackpressure](../ActorSource/actorRefWithBackpressure.md) The corresponding operator for the new actors API
* @ref[Source.queue](../Source/queue.md) Materialize a `SourceQueue` onto which elements can be pushed for emitting from the source

## Examples

Scala
:  @@snip [actorRef.scala](/docs/src/test/scala/docs/stream/operators/SourceOperators.scala) { #actorRefWithBackpressure }

Java
:  @@snip [actorRef.java](/docs/src/test/java/jdocs/stream/operators/SourceDocExamples.java) { #actor-ref-imports #actorRefWithBackpressure }

## Reactive Streams semantics

@@@div { .callout }

**emits** when there is demand and there are messages in the buffer or a message is sent to the `ActorRef`

**completes** when the passed completion matcher returns a `CompletionStrategy` or fails if the passed failure matcher returns an exception

@@@
