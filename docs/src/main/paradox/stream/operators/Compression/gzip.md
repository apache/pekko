# Compression.gzip

Creates a flow that gzip-compresses a stream of ByteStrings.  

@ref[Compression operators](../index.md#compression-operators)

## Signature

@apidoc[Compression.gzip](stream.*.Compression$) { scala="#gzip:org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#gzip()" }
@apidoc[Compression.gzip](stream.*.Compression$) { scala="#gzip(level:Int):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#gzip(int)" }
@apidoc[Compression.gzip](stream.*.Compression$) { scala="#gzip(level:Int,autoFlush:Boolean):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#gzip(int,boolean)" }

## Description

Creates a flow that gzip-compresses a stream of ByteStrings. Note that the compressor
will SYNC_FLUSH after every @apidoc[util.ByteString] so that it is guaranteed that every @apidoc[util.ByteString]
coming out of the flow can be fully decompressed without waiting for additional data. This may
come at a compression performance cost for very small chunks.

Use the overload method to control the compression level.

Since 1.3.0, an overload with an `autoFlush` parameter is also available. If `autoFlush` is `true` (the default for the
other overloads), the compressor flushes after every single element in the stream. If it is `false`, the compressor
only emits output when the compression algorithm produces it, which can improve the compression ratio for small
chunks, but an emitted `ByteString` may not be fully decompressible until more data arrives.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the compression algorithm produces output for the received `ByteString`

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
