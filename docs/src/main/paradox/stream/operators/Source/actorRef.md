# Source.actorRef

Materialize an `ActorRef` of the classic actors API; sending messages to it will emit them on the stream.

@ref[Actor interop operators](../index.md#actor-interop-operators)

## Signature

@apidoc[Source.actorRef](Source$) { scala="#actorRef[T](completionMatcher:PartialFunction[Any,org.apache.pekko.stream.CompletionStrategy],failureMatcher:PartialFunction[Any,Throwable],bufferSize:Int,overflowStrategy:org.apache.pekko.stream.OverflowStrategy):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.actor.ActorRef]" java="#actorRef(org.apache.pekko.japi.function.Function,org.apache.pekko.japi.function.Function,int,org.apache.pekko.stream.OverflowStrategy)" }

## Description

Materialize an `ActorRef`, sending messages to it will emit them on the stream. The actor contains
a buffer but since communication is one way, there is no back pressure. Handling overflow is done by either dropping
elements or failing the stream; the strategy is chosen by the user.

The stream can be completed successfully by sending the actor reference a message that is matched by the
`completionMatcher`. If the matcher returns `org.apache.pekko.stream.CompletionStrategy.immediately` the completion
will be signaled immediately. If it returns `org.apache.pekko.stream.CompletionStrategy.draining`, already buffered
elements will be sent out before signaling completion.
The stream can be failed by sending a message that is matched by the `failureMatcher`; the extracted `Throwable` is
used to fail the stream.
`org.apache.pekko.actor.PoisonPill` and `org.apache.pekko.actor.Kill` messages are ignored (a warning is logged) and
do not complete the stream. Using `org.apache.pekko.actor.ActorSystem.stop` to stop the actor and complete the stream is *not supported*.

See also:

* @ref[Source.actorRefWithBackpressure](../Source/actorRefWithBackpressure.md) This operator, but with backpressure control
* @ref[ActorSource.actorRef](../ActorSource/actorRef.md) The corresponding operator for the new actors API
* @ref[ActorSource.actorRefWithBackpressure](../ActorSource/actorRefWithBackpressure.md) The operator for the new actors API with backpressure control
* @ref[Source.queue](../Source/queue.md) Materialize a `SourceQueue` onto which elements can be pushed for emitting from the source

## Examples

Scala
:  @@snip [actorRef.scala](/docs/src/test/scala/docs/stream/operators/SourceOperators.scala) { #actorRef }

Java
:  @@snip [actorRef.java](/docs/src/test/java/jdocs/stream/operators/SourceDocExamples.java) { #actor-ref-imports #actor-ref }

## Reactive Streams semantics

@@@div { .callout }

**emits** when there is demand and there are messages in the buffer or a message is sent to the `ActorRef`

**completes** when the actor is stopped by sending it a particular message as described above

@@@
