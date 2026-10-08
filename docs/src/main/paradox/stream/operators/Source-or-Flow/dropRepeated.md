# dropRepeated

Only pass on those elements that are distinct from the previous element.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.dropRepeated](Source) { scala="#dropRepeated():FlowOps.this.Repr[Out]" java="#dropRepeated()" }
@apidoc[Flow.dropRepeated](Flow) { scala="#dropRepeated():FlowOps.this.Repr[Out]" java="#dropRepeated()" }
@apidoc[Source.dropRepeated](Source) { scala="#dropRepeated(p:(Out,Out)=&gt;Boolean):FlowOps.this.Repr[Out]" java="#dropRepeated(org.apache.pekko.japi.function.Function2)" }
@apidoc[Flow.dropRepeated](Flow) { scala="#dropRepeated(p:(Out,Out)=&gt;Boolean):FlowOps.this.Repr[Out]" java="#dropRepeated(org.apache.pekko.japi.function.Function2)" }


## Description

Only pass on those elements that are distinct from the previous element.

By default elements are compared using equality. An overload accepting a predicate `p` can be used instead,
in which case an element is dropped when `p` returns `true` for the previous element and the current element.

## Example

For example, given a `Source` of numbers, we just want to pass distinct numbers downstream:

Scala
:  @@snip [Filter.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Filter.scala) { #dropRepeated }

Java
:  @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #dropRepeated }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the element is distinct from the previous element

**backpressures** when the element is distinct from the previous element and downstream backpressures

**completes** when upstream completes

@@@

## API docs

@apidoc[Flow.dropRepeated](Flow) { scala="#dropRepeated():FlowOps.this.Repr[Out]" java="#dropRepeated()" }
