# recoverWith

Allow switching to alternative Source when a failure has happened upstream.

@ref[Error handling](../index.md#error-handling)

## Signature

@apidoc[Source.recoverWith](Source) { scala="#recoverWith[T&gt;:Out](pf:PartialFunction[Throwable,org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[T],org.apache.pekko.NotUsed]]):FlowOps.this.Repr[T]" java="#recoverWith(java.lang.Class,org.apache.pekko.japi.function.Creator)" }
@apidoc[Flow.recoverWith](Flow) { scala="#recoverWith[T&gt;:Out](pf:PartialFunction[Throwable,org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[T],org.apache.pekko.NotUsed]]):FlowOps.this.Repr[T]" java="#recoverWith(java.lang.Class,org.apache.pekko.japi.function.Creator)" }


## Description

Allow switching to alternative Source when a failure has happened upstream.

Throwing an exception inside `recoverWith` _will_ be logged on ERROR level automatically.

## Reactive Streams semantics

@@@div { .callout }

**emits** the element is available from the upstream or upstream is failed and pf returns alternative Source

**backpressures** downstream backpressures, after failure happened it backprssures to alternative Source

**completes** upstream completes or upstream failed with exception pf can handle

@@@

