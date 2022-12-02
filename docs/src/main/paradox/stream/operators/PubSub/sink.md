# PubSub.sink

A sink that will publish emitted messages to a @apidoc[actor.typed.pubsub.Topic$].

@ref[Actor interop operators](../index.md#actor-interop-operators)

Note that there is no backpressure from the topic, so care must be taken to not publish messages at a higher rate than that can be handled 
by subscribers.

If the topic does not have any subscribers when a message is published, or the topic actor is stopped, the message is sent to dead letters.

## Dependency

This operator is included in:

@@dependency[sbt,Maven,Gradle] {
bomGroup=com.typesafe.akka bomArtifact=akka-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
symbol1=PekkoVersion
value1="$pekko.version$"
group="com.typesafe.akka"
artifact="akka-stream-typed_$scala.binary.version$"
version=PekkoVersion
}

## Signature

@apidoc[PubSub.sink](stream.typed.*.PubSub$) { scala="#sink[T](topic:org.apache.pekko.actor.typed.Toppic[T]):org.apache.pekko.stream.scaladsl.Sink[T,org.apache.pekko.NotUsed]" java="#sink(org.apache.pekko.actor.typed.Topic)" }

## Reactive Streams semantics

@@@div { .callout }

**cancels** never

**backpressures** never

@@@
