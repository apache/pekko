# splitAfter

End the current substream whenever a predicate returns `true`, starting a new substream for the next element.

@ref[Nesting and flattening operators](../index.md#nesting-and-flattening-operators)

## Signature

@apidoc[Source.splitAfter](Source) { scala="#splitAfter(p:Out=&gt;Boolean):org.apache.pekko.stream.scaladsl.SubFlow[Out,Mat,FlowOps.this.Repr,FlowOps.this.Closed]" java="#splitAfter(org.apache.pekko.japi.function.Predicate)" }
@apidoc[Flow.splitAfter](Flow) { scala="#splitAfter(p:Out=&gt;Boolean):org.apache.pekko.stream.scaladsl.SubFlow[Out,Mat,FlowOps.this.Repr,FlowOps.this.Closed]" java="#splitAfter(org.apache.pekko.japi.function.Predicate)" }

The `splitAfter` operator adheres to the ActorAttributes.SupervisionStrategy attribute with the caveat that
`Supervision.restart` behaves the same way as `Supervision.resume` since `Supervision.restart` for `SubFlow`s semantically doesn't make sense.

## Description

End the current substream whenever a predicate returns `true`, starting a new substream for the next element.

## Example

Given some time series data source we would like to split the stream into sub-streams for each second.
By using `sliding` we can compare the timestamp of the current and next element to decide when to split.

Scala
:  @@snip [Scan.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Split.scala) { #splitAfter }

Java
:  @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/sourceorflow/Split.java) { #splitAfter }

An alternative way of implementing this is shown in @ref:[splitWhen example](splitWhen.md#example).

## Reactive Streams semantics

@@@div { .callout }

**emits** when an element passes through. When the provided predicate is true it emits the element * and opens a new substream for subsequent element

**backpressures** when there is an element pending for the next substream, but the previous is not fully consumed yet, or the substream backpressures

**completes** when upstream completes (Until the end of stream it is not possible to know whether new substreams will be needed or not)

@@@

