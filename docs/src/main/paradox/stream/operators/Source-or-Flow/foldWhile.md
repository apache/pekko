# foldWhile

Start with current value `zero` and then apply the current and next value to the given function. When upstream completes or the predicate `p` returns `false`, the current value is emitted downstream.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.foldWhile](Source) { scala="#foldWhile%5BT%5D(zero%3A%20T)(p%3A%20T%20%3D%3E%20Boolean)(f%3A%20(T%2C%20Out)%20%3D%3E%20T):FlowOps.this.Repr[T]" java="#foldWhile(java.lang.Object,org.apache.pekko.japi.function.Predicate,org.apache.pekko.japi.function.Function2)" }
@apidoc[Flow.foldWhile](Flow) { scala="#foldWhile%5BT%5D(zero%3A%20T)(p%3A%20T%20%3D%3E%20Boolean)(f%3A%20(T%2C%20Out)%20%3D%3E%20T):FlowOps.this.Repr[T]" java="#foldWhile(java.lang.Object,org.apache.pekko.japi.function.Predicate,org.apache.pekko.japi.function.Function2)" }

## Description

Start with current value `zero` and then apply the current and next value to the given function. When upstream
completes, the current value is emitted downstream.

@@@ warning

Note that the `zero` value must be immutable, because otherwise
the same mutable instance would be shared across different threads
when running the stream more than once.

@@@

## Example

`foldWhile` is typically used to 'fold up' the incoming values into an aggregate with a predicate. 
For example, you can use `foldWhile` to calculate the sum while some predicate is true.


Scala
:   @@snip [FoldWhile.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/FoldWhile.scala) { #imports #foldWhile }

Java
:   @@snip [FoldWhile.java](/docs/src/test/java/jdocs/stream/operators/flow/FoldWhile.java) { #foldWhile }

## Reactive Streams semantics

@@@div { .callout }

**emits** when upstream completes

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@

