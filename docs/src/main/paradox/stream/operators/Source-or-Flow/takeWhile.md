# takeWhile

Pass elements downstream as long as a predicate function returns true and then complete. 

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.takeWhile](Source) { scala="#takeWhile(p:Out=&gt;Boolean):FlowOps.this.Repr[Out]" java="#takeWhile(org.apache.pekko.japi.function.Predicate)" }
@apidoc[Source.takeWhile](Source) { scala="#takeWhile(p:Out=&gt;Boolean,inclusive:Boolean):FlowOps.this.Repr[Out]" java="#takeWhile(org.apache.pekko.japi.function.Predicate,boolean)" }
@apidoc[Flow.takeWhile](Flow) { scala="#takeWhile(p:Out=&gt;Boolean):FlowOps.this.Repr[Out]" java="#takeWhile(org.apache.pekko.japi.function.Predicate)" }
@apidoc[Flow.takeWhile](Flow) { scala="#takeWhile(p:Out=&gt;Boolean,inclusive:Boolean):FlowOps.this.Repr[Out]" java="#takeWhile(org.apache.pekko.japi.function.Predicate,boolean)" }


## Description

Pass elements downstream as long as a predicate function returns true and then complete. 
The element for which the predicate returns false is not emitted, unless the overload with `inclusive` set to `true`
is used, in which case that first failing element is also emitted before completing.

## Example

Scala
:  @@snip [TakeWhile.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/TakeWhile.scala) { #take-while }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #take-while }

## Reactive Streams semantics

@@@div { .callout }

**emits** while the predicate is true and until the first false result

**backpressures** when downstream backpressures

**completes** when predicate returned false or upstream completes

@@@
