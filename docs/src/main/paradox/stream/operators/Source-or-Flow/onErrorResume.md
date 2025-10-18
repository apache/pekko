# onErrorResume

Allows transforming a failure signal into a stream of elements provided by a factory function.

@ref[Error handling](../index.md#error-handling)

This method is Java API only, use @ref[recoverWith](recoverWith.md) in Scala.

## Signature

@apidoc[Source.onErrorResume](Source) { java="#onErrorResume(org.apache.pekko.japi.function.Function)" }<br>
@apidoc[Source.onErrorResume](Source) { java="#onErrorResume(java.lang.Class,org.apache.pekko.japi.function.Function)" }<br>
@apidoc[Source.onErrorResume](Source) { java="#onErrorResume(org.apache.pekko.japi.function.Predicate,org.apache.pekko.japi.function.Function)" }<br>
@apidoc[Flow.onErrorResume](Flow) { java="#onErrorResume(org.apache.pekko.japi.function.Function)" }<br>
@apidoc[Flow.onErrorResume](Flow) { java="#onErrorResume(java.lang.Class,org.apache.pekko.japi.function.Function)" }<br>
@apidoc[Flow.onErrorResume](Flow) { java="#onErrorResume(org.apache.pekko.japi.function.Predicate,org.apache.pekko.japi.function.Function)" }


## Description

Transform a failure signal into a stream of elements provided by a factory function.

## Reactive Streams semantics

@@@div { .callout }

**emits** element is available from the upstream or upstream is failed and fallback Source produces an element

**backpressures** downstream backpressures

**completes** upstream completes or upstream failed with exception and fallback Source completes

**Cancels when** downstream cancels
@@@