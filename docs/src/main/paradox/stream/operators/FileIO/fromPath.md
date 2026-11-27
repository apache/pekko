<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# FileIO.fromPath

Emits the contents of a file from the given path.

@ref[File IO Sinks and Sources](../index.md#file-io-sinks-and-sources)

## Signature

@apidoc[FileIO.fromPath](FileIO$) { scala="#fromPath(f:java.nio.file.Path,chunkSize:Int,startPosition:Long):org.apache.pekko.stream.scaladsl.Source[org.apache.pekko.util.ByteString,scala.concurrent.Future[org.apache.pekko.stream.IOResult]]" java="#fromPath(java.nio.file.Path,int,long)" }


## Description

Emits the contents of a file from the given path, as `ByteString`s, materializes into a @scala[`Future`] @java[`CompletionStage`] which will be completed with
an `IOResult` on end-of-file, cancellation, or failure. If the source opened the file, it attempts to close it before
completing the materialized result.

Emitted elements have the configured `chunkSize` in bytes, except that the final element may be smaller.
A short read that returns some bytes does not by itself end the stream.

## Example

Scala
:  @@snip [StreamFileDocSpec.scala](/docs/src/test/scala/docs/stream/io/StreamFileDocSpec.scala) { #file-source }

Java
:  @@snip [StreamFileDocTest.java](/docs/src/test/java/jdocs/stream/io/StreamFileDocTest.java) { #file-source }

