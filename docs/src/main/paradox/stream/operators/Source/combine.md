# Source.combine

Combine several sources, using a given strategy such as merge or concat, into one source.

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.combine](Source$) { scala="#combine[T,U](first:org.apache.pekko.stream.scaladsl.Source[T,_],second:org.apache.pekko.stream.scaladsl.Source[T,_],rest:org.apache.pekko.stream.scaladsl.Source[T,_]*)(strategy:Int=&gt;org.apache.pekko.stream.Graph[org.apache.pekko.stream.UniformFanInShape[T,U],org.apache.pekko.NotUsed]):org.apache.pekko.stream.scaladsl.Source[U,org.apache.pekko.NotUsed]" java="#combine(org.apache.pekko.stream.javadsl.Source,org.apache.pekko.stream.javadsl.Source,java.util.List,org.apache.pekko.japi.function.Function)" }


## Description

Provides a way to create a "fan-in" of multiple sources without having to use the more advanced @ref:[GraphDSL](../../stream-graphs.md#constructing-graphs).

The way the elements from the sources
are combined is pluggable through the `strategy` parameter which accepts a function 
@scala[`Int => Graph[FanInShape]`]@java[`Integer -> Graph<FanInShape>`] where the integer parameter specifies the number of sources
that the graph must accept. This makes it possible to use `combine` with the built-in `Concat` 
and `Merge` by @scala[expanding their `apply` methods to functions]@java[using a method reference to their `create` methods],
but also to use an arbitrary strategy. 

Combine is most useful when you have more sources than 2 or want to use a custom operator, as there are more concise 
operators for 2-source @ref:[concat](../Source-or-Flow/concat.md) and @ref:[merge](../Source-or-Flow/merge.md) 

Some of the built-in operators that can be used as strategy are:
 
 * @apidoc[stream.*.Merge] 
 * @apidoc[stream.(javadsl|scaladsl).Concat] 
 * @apidoc[MergePrioritized]
 * @apidoc[MergeLatest]
 * @apidoc[ZipN]
 * @apidoc[ZipWithN]

## Examples

In this example we `Merge` three different 
sources of integers. The three sources will immediately start contributing elements to the combined source. The individual 
elements from each source will be in order but the order compared to elements from other sources is not deterministic:

Scala
:   @@snip [Combine.scala](/docs/src/test/scala/docs/stream/operators/source/Combine.scala) { #imports #source-combine-merge }   

Java
:   @@snip [Combine.java](/docs/src/test/java/jdocs/stream/operators/source/Combine.java) { #imports #source-combine-merge }


If we instead use `Concat` the first source 
will get to emit elements until it completes, then the second source until that completes and so on until all the sources has completed.

Scala
:   @@snip [Combine.scala](/docs/src/test/scala/docs/stream/operators/source/Combine.scala) { #source-combine-concat }   

Java
:   @@snip [Combine.java](/docs/src/test/java/jdocs/stream/operators/source/Combine.java) { #source-combine-concat }


## Reactive Streams semantics

@@@div { .callout }

**emits** when there is demand, but depending on the strategy

**completes** depends on the strategy

@@@
