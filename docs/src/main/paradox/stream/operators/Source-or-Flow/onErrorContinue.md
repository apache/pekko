# onErrorContinue

Continues the stream when an upstream error occurs.

@ref[Error handling](../index.md#error-handling)

## Signature

@apidoc[Source.onErrorContinue](Source) { scala="#onErrorContinue(errorConsumer%3A%20Function%5BThrowable%2C%20Unit%5D)%3AFlowOps.this.Repr%5BT%5D" java="#onErrorContinue(org.apache.pekko.japi.function.Procedure)" }
@apidoc[Flow.onErrorContinue](Flow) { scala="#onErrorContinue%5BT%20%3C%3A%20Throwable%5D(errorConsumer%3A%20Function%5BThrowable%2C%20Unit%5D)(implicit%20tag%3A%20ClassTag%5BT%5D)%3AFlowOps.this.Repr%5BT%5D" java="#onErrorContinue(java.lang.Class,org.apache.pekko.japi.function.Procedure)" }

## Description

Continues the stream when an upstream error occurs.

## Reactive Streams semantics

@@@div { .callout }

**emits** element is available from the upstream

**backpressures** downstream backpressures

**completes** upstream completes or upstream failed with exception this operator can't handle

**Cancels when** downstream cancels
@@@