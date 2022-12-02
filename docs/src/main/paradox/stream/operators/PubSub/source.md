# PubSub.source

A source that will subscribe to a @apidoc[actor.typed.pubsub.Topic$] and stream messages published to the topic. 

@ref[Actor interop operators](../index.md#actor-interop-operators)

The source can be materialized  multiple times, each materialized stream will stream messages published to the topic after the stream has started.

Note that it is not possible to propagate the backpressure from the running stream to the pub sub topic,
if the stream is backpressuring published messages are buffered up to a limit and if the limit is hit
the configurable `OverflowStrategy` decides what happens. It is not possible to use the `Backpressure`
strategy.


## Dependency

This operator is included in:

@@dependency[sbt,Maven,Gradle] {
bomGroup=com.typesafe.akka bomArtifact=akka-bom_$scala.binary.version$ bomVersionSymbols=AkkaVersion
symbol1=AkkaVersion
value1="$akka.version$"
group="com.typesafe.akka"
artifact="akka-stream-typed_$scala.binary.version$"
version=AkkaVersion
}

## Signature

@apidoc[PubSub.source](stream.typed.*.PubSub$) { scala="#source[T](topic:org.apache.pekko.actor.typed.Topic[T]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#source(org.apache.pekko.actor.typed.Topic)" }

## Reactive Streams semantics

@@@div { .callout }

**emits** a message published to the topic is emitted as soon as there is demand from downstream

**completes** when the topic actor terminates 

@@@