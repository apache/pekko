# zip

Combines elements from each of multiple sources into @scala[tuples] @java[*Pair*] and passes the @scala[tuples] @java[pairs] downstream.

@ref[Fan-in operators](../index.md#fan-in-operators)

## Signature

@apidoc[Source.zip](Source$) { scala="#zip[U](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[U],_]):FlowOps.this.Repr[(Out,U)]" java="#zip(org.apache.pekko.stream.Graph)" }
@apidoc[Flow.zip](Flow) { scala="#zip[U](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[U],_]):FlowOps.this.Repr[(Out,U)]" java="#zip(org.apache.pekko.stream.Graph)" }

## Description

Combines elements from each of multiple sources into @scala[tuples] @java[*Pair*] and passes the @scala[tuples] @java[pairs] downstream.

See also:

 * @ref:[zipAll](zipAll.md)
 * @ref:[zipWith](zipWith.md)
 * @ref:[zipWithIndex](zipWithIndex.md)  

## Examples

Scala
:   @@snip [FlowZipSpec.scala](/stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/FlowZipSpec.scala) { #zip }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #zip }

## Reactive Streams semantics

@@@div { .callout }

**emits** when both of the inputs have an element available

**backpressures** both upstreams when downstream backpressures but also on an upstream that has emitted an element until the other upstream has emitted an element

**completes** when either upstream completes

@@@
