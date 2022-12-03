# Source.range

Emit each integer in a range, with an option to take bigger steps than 1.

@ref[Source operators](../index.md#source-operators)

## Dependency

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=akka-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group="org.apache.pekko"
  artifact="akka-stream_$scala.binary.version$"
  version=PekkoVersion
}


## Description

Emit each integer in a range, with an option to take bigger steps than 1. @scala[In Scala, use the `apply` method to generate a sequence of integers.]

## Examples

Define the range of integers.

Java
:   @@snip [SourceDocExamples.java](/docs/src/test/java/jdocs/stream/operators/SourceDocExamples.java) { #range-imports #range }

Print out the stream of integers.

Java
:   @@snip [SourceDocExamples.java](/docs/src/test/java/jdocs/stream/operators/SourceDocExamples.java) { #run-range}

## Reactive Streams semantics

@@@div { .callout }

**emits** when there is demand, the next value

**completes** when the end of the range has been reached

@@@
