# Source.iterate

Creates a sequential `Source` by iterating with the given predicate, function and seed.

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.iterate](Source$) { scala="#iterate%5BT](seed:T)(hasNext:T=&gt;Boolean,next:T=&gt;T):org.apache.pekko.stream.scaladsl.Source%5BT,org.apache.pekko.NotUsed]" java="#iterate(java.lang.Object,org.apache.pekko.japi.function.Predicate,org.apache.pekko.japi.function.Function)" }


## Description

Creates a sequential Source by iterating with the given hasNext predicate and next function,
starting with the given seed value. If the hasNext function returns false for the seed, the Source completes with empty.

@@@ warning

The same `seed` value will be used for every materialization of the `Source` so it is **mandatory** that the state is immutable. For example a `java.util.Iterator`, `Array` or Java standard library collection would not be safe as the fold operation could mutate the value. If you must use a mutable value, combining with @ref:[Source.lazySource](lazySource.md) to make sure a new mutable `zero` value is created for each materialization is one solution.

@@@

## Examples

The next example shows how to craet

Scala
 :   @@snip [Iterate.scala](/docs/src/test/scala/docs/stream/operators/source/Iterate.scala) { #countTo }
 
Java
 :   @@snip [Iterate.java](/docs/src/test/java/jdocs/stream/operators/source/Iterate.java) { #countTo }


## Reactive Streams semantics

@@@div { .callout }

**emits** when there is demand and the `next` function returns.

**completes** when the `haxNext` predicate returns false.

@@@

