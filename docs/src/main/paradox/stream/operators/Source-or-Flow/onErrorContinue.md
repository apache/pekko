# onErrorContinue

Continues the stream when an upstream error occurs.

@ref[Error handling](../index.md#error-handling)

## Signature

@apidoc[Source.onErrorContinue](Source) { scala="#onErrorContinue[T%3C:Throwable](errorConsumer:Throwable=%3EUnit)(implicittag:scala.reflect.ClassTag[T]):FlowOps.this.Repr[Out]" java="#onErrorContinue(org.apache.pekko.japi.function.Procedure)" }
@apidoc[Flow.onErrorContinue](Flow) { scala="#onErrorContinue[T%3C:Throwable](errorConsumer:Throwable=%3EUnit)(implicittag:scala.reflect.ClassTag[T]):FlowOps.this.Repr[Out]" java="#onErrorContinue(java.lang.Class,org.apache.pekko.japi.function.Procedure)" }

@apidoc[Source.onErrorContinue](Source) { scala="#onErrorContinue(p:Throwable=%3EBoolean)(errorConsumer:Throwable=%3EUnit):FlowOps.this.Repr[Out]" java="#onErrorContinue(org.apache.pekko.japi.function.Predicate,org.apache.pekko.japi.function.Procedure)" }
@apidoc[Source.onErrorContinue](Source) { java="#onErrorContinue(java.lang.Class,org.apache.pekko.japi.function.Procedure)" }
@apidoc[Flow.onErrorContinue](Flow) { scala="#onErrorContinue(p:Throwable=%3EBoolean)(errorConsumer:Throwable=%3EUnit):FlowOps.this.Repr[Out]" java="#onErrorContinue(org.apache.pekko.japi.function.Predicate,org.apache.pekko.japi.function.Procedure)" }
@apidoc[Flow.onErrorContinue](Flow) { java="#onErrorContinue(org.apache.pekko.japi.function.Procedure)" }

## Description

Continues the stream when an upstream error occurs.

When an error is signaled from upstream, the `errorConsumer` function is invoked with the `Throwable`, and the stream
resumes processing subsequent elements. The element that caused the error is dropped.

Which errors are handled can be restricted @scala[by the type parameter `T`]@java[by passing a `Class`], or by passing
a predicate `p` that decides for each error whether it should be handled. Errors that are not handled fail the
stream as usual.

This operator relies on supervision, so it has no effect on operators that do not support supervision.

## Reactive Streams semantics

@@@div { .callout }

**emits** element is available from the upstream

**backpressures** downstream backpressures

**completes** upstream completes

**fails** upstream failed with exception this operator can't handle

**Cancels when** downstream cancels
@@@