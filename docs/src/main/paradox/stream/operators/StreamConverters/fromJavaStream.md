# StreamConverters.fromJavaStream

Create a source that wraps a Java 8 `java.util.stream.Stream`.

@ref[Additional Sink and Source converters](../index.md#additional-sink-and-source-converters)

## Signature

@apidoc[StreamConverters](StreamConverters$) { scala="#fromJavaStream%5BT,S%3C:java.util.stream.BaseStream[T,S]](stream:()=%3Ejava.util.stream.BaseStream[T,S]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#fromJavaStream(org.apache.pekko.japi.function.Creator)" }

## Example

Here is an example of a @apidoc[Source] created from a @javadoc[java.util.stream.Stream](java.util.stream.Stream).

Scala
:   @@snip [StreamConvertersToJava.scala](/docs/src/test/scala/docs/stream/operators/converters/StreamConvertersToJava.scala) { #import #fromJavaStream }

Java
:   @@snip [StreamConvertersToJava.java](/docs/src/test/java/jdocs/stream/operators/converters/StreamConvertersToJava.java) { #import #fromJavaStream }
