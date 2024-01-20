# exists

Completes the stream with true as soon as there's an element satisfy the *predicate*, or emits false when the stream
completes and no element satisfied the *predicate*.
@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Flow.exists](Flow$) { scala="#exists[I](p:Out%20=%3E%20Boolean)" java="#exists[I](p:org.apache.pekko.japi.function.Predicate[I])" }

@apidoc[Sink.exists](Sink$) { scala="#exists[I](p:Out%20=%3E%20Boolean)" java="#exists[I](p:org.apache.pekko.japi.function.Predicate[I])" }

## Description

Completes the stream with true as soon as there's an element satisfy the *predicate*, or emits false when the stream
completes and no element satisfied the *predicate*.

## Examples

Scala
:  @@snip [Exists.scala](/docs/src/test/scala/docs/stream/operators/sink/Exists.scala) { #imports #exists }

Java
:  @@snip [Exists.java](/docs/src/test/java/jdocs/stream/operators/sink/Exists.java) { #imports #exists }