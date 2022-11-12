# Compression.inflate

Creates a flow that deflate-decompresses a stream of ByteStrings. 

@ref[Compression operators](../index.md#compression-operators)

## Signature

@apidoc[Compression.indeflate](stream.*.Compression$) { scala="#inflate(maxBytesPerChunk:Int,nowrap:Boolean):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#inflate(int,boolean)" }

## Description

Creates a flow that deflate-decompresses a stream of ByteStrings.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the compression algorithm produces output for the received `ByteString` (the emitted `ByteString` is of `maxBytesPerChunk` maximum length)

**backpressures** when downstream backpressures

**completes** when upstream completes (may emit finishing bytes in an extra `ByteString` )

@@@
