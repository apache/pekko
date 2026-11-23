<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Sink.ignore

Consume all elements but discards them.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.ignore](Sink$) { java="#ignore()" }


## Description

Consume all elements but discards them. Useful when a stream has to be consumed but there is no use to actually
do anything with the elements in the `Sink`. Processing of the elements may occur in the `Source`/`Flow`. 

## Example

This examples reads lines from a file, saves them to a database, and stores the database identifiers in
another file. The stream is run with `Sink.ignore` because all processing of the elements have been performed
by the preceding stream operators.

Scala
:   @@snip [Ignore.scala](/docs/src/test/scala/docs/stream/operators/sink/Ignore.scala) { #ignore }

Java
:   @@snip [SinkDocExamples.java](/docs/src/test/java/jdocs/stream/operators/SinkDocExamples.java) { #ignore }


## Reactive Streams semantics

@@@div { .callout }

**cancels** never

**backpressures** never

@@@


