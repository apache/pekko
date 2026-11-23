<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Compression.gzipDecompress

Creates a flow that gzip-decompresses a stream of ByteStrings.  

@ref[Compression operators](../index.md#compression-operators)

## Signature

@apidoc[Compression.gzipDecompress](stream.*.Compression$) { scala="#gzipDecompress(maxBytesPerChunk:Int):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#gzipDecompress(int)" }

## Description

Creates a flow that gzip-decompresses a stream of ByteStrings. If the input is truncated, uses invalid 
compression method or is invalid (failed CRC checks) this operator fails with a `java.util.zip.ZipException`. 

## Reactive Streams semantics

@@@div { .callout }

**emits** when the decompression algorithm produces output for the received `ByteString` (the emitted `ByteString` is of `maxBytesPerChunk` maximum length)

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
