# onErrorContinue

Continues the stream when an upstream error occurs.

@ref[Error handling](../index.md#error-handling)

## Signature

@apidoc[Source.onErrorContinue](Source) { scala="#onErrorContinue[T%3C:Throwable](errorConsumer:Throwable=%3EUnit)(implicittag:scala.reflect.ClassTag[T]):FlowOps.this.Repr[Out]" java="#onErrorContinue(org.apache.pekko.japi.function.Procedure)" }
@apidoc[Flow.onErrorContinue](Flow) { scala="#onErrorContinue[T%3C:Throwable](errorConsumer:Throwable=%3EUnit)(implicittag:scala.reflect.ClassTag[T]):FlowOps.this.Repr[Out]" java="#onErrorContinue(java.lang.Class,org.apache.pekko.japi.function.Procedure)" }

## Description

Continues the stream when an upstream error occurs.

## Reactive Streams semantics

@@@div { .callout }

**emits** element is available from the upstream

**backpressures** downstream backpressures

**completes** upstream completes or upstream failed with exception this operator can't handle

**Cancels when** downstream cancels
@@@