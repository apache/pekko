# Sink.asJavaStream

Create a sink which materializes into Java 8 `Stream` that can be run to trigger demand through the sink.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.asJavaStream](Sink$) { scala="#asJavaStream[T]():org.apache.pekko.stream.scaladsl.Sink[T,java.util.stream.Stream[T]]" java="#asJavaStream()" }

## Description

Create a sink which materializes into Java 8 `Stream` that can be run to trigger demand through the sink.
Elements emitted through the stream will be available for reading through the Java 8 `Stream`.

The Java 8 `Stream` will be ended when the stream flowing into this `Sink` completes, and closing the Java
`Stream` will cancel the inflow of this `Sink`. If the Java `Stream` throws an exception, the Pekko stream is cancelled.

Be aware that Java `Stream` blocks current thread while waiting on next element from downstream.

## Example

Here is an example of a @apidoc[Sink] that materializes into a @javadoc[java.util.stream.Stream](java.util.stream.Stream). 

Scala
:   @@snip [StreamConvertersToJava.scala](/docs/src/test/scala/docs/stream/operators/converters/StreamConvertersToJava.scala) { #import #asJavaStreamOnSink }

Java
:   @@snip [StreamConvertersToJava.java](/docs/src/test/java/jdocs/stream/operators/converters/StreamConvertersToJava.java) { #import #asJavaStreamOnSink }


## Reactive Streams semantics

@@@div { .callout }
**cancels** when the Java Stream is closed

**backpressures** when no read is pending on the Java Stream
@@@
