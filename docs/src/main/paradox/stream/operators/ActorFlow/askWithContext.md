# ActorFlow.askWithContext

Use the "Ask Pattern" to send each stream element (without the context) as an `ask` to the target actor (of the new actors API), and expect a reply that will be emitted downstream.

@ref[Actor interop operators](../index.md#actor-interop-operators)

## Dependency

This operator is included in:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=pekko-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group="org.apache.pekko"
  artifact="pekko-stream-typed_$scala.binary.version$"
  version=PekkoVersion
}

## Signature

@apidoc[ActorFlow.askWithContext](ActorFlow$) { scala="#askWithContext%5BI,Q,A,Ctx](ref:org.apache.pekko.actor.typed.ActorRef%5BQ])(makeMessage:(I,org.apache.pekko.actor.typed.ActorRef%5BA])=%3EQ)(implicittimeout:org.apache.pekko.util.Timeout):org.apache.pekko.stream.scaladsl.Flow%5B(I,Ctx),(A,Ctx),org.apache.pekko.NotUsed]" java="#askWithContext(org.apache.pekko.actor.typed.ActorRef,java.time.Duration,org.apache.pekko.japi.function.Function2)" }

## Description

Use the @ref[Ask pattern](../../../typed/interaction-patterns.md#request-response-with-ask-from-outside-an-actor) to send a request-reply message to the target `ref` actor.
The stream context is not sent, instead it is locally recombined to the actor's reply.

If any of the asks times out it will fail the stream with an @apidoc[AskTimeoutException].

The `ask` operator requires

* the actor `ref`,
* a `makeMessage` function to create the message sent to the actor from the incoming element, and the actor ref accepting the actor's reply message 
* a timeout.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the futures (in submission order) created by the ask pattern internally are completed

**backpressures** when the number of futures reaches the configured parallelism and the downstream backpressures

**completes** when upstream completes and all futures have been completed and all elements have been emitted

**fails** when the passed-in actor terminates, or when any of the `ask`s exceed a timeout

**cancels** when downstream cancels

@@@
