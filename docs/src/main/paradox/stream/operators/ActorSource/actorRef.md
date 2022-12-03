# ActorSource.actorRef

Materialize an @java[`ActorRef<T>`]@scala[`ActorRef[T]`] of the new actors API; sending messages to it will emit them on the stream only if they are of the same type as the stream.

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

@apidoc[ActorSource.actorRef](ActorSource$) { scala="#actorRef[T](completionMatcher:PartialFunction[T,Unit],failureMatcher:PartialFunction[T,Throwable],bufferSize:Int,overflowStrategy:org.apache.pekko.stream.OverflowStrategy):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.actor.typed.ActorRef[T]]" java="#actorRef(java.util.function.Predicate,org.apache.pekko.japi.function.Function,int,org.apache.pekko.stream.OverflowStrategy)" }

## Description

Materialize an @java[`ActorRef<T>`]@scala[`ActorRef[T]`] which only accepts messages that are of the same type as the stream.

See also:

* @ref[ActorSource.actorRefWithBackpressure](actorRefWithBackpressure.md) This operator, but with backpressure control
* @ref[Source.actorRef](../Source/actorRef.md) The corresponding operator for the classic actors API
* @ref[Source.actorRefWithBackpressure](../Source/actorRefWithBackpressure.md) The operator for the classic actors API with backpressure control
* @ref[Source.queue](../Source/queue.md) Materialize a `SourceQueue` onto which elements can be pushed for emitting from the source

## Examples

Scala
:  @@snip [ActorSourceSinkExample.scala](/akka-stream-typed/src/test/scala/docs/org/apache/pekko/stream/typed/ActorSourceSinkExample.scala) { #actor-source-ref }

Java
:  @@snip [ActorSourceExample.java](/akka-stream-typed/src/test/java/docs/org/apache/pekko/stream/typed/ActorSourceExample.java) { #actor-source-ref }
