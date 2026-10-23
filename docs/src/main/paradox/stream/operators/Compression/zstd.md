# Compression.zstd

Creates a flow that zstd-compresses a stream of ByteStrings.

@ref[Compression operators](../index.md#compression-operators)

## Dependency

The zstd operators use [zstd-jni](https://github.com/luben/zstd-jni), an optional dependency of Pekko Streams
that has to be added explicitly:

@@dependency[sbt,Maven,Gradle] {
  group=com.github.luben
  artifact=zstd-jni
  version="$zstd_jni_version$"
}

## Signature

@apidoc[Compression.zstd](stream.*.Compression$) { scala="#zstd:org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#zstd()" }
@apidoc[Compression.zstd](stream.*.Compression$) { scala="#zstd(level:Int,dictionary:Option[org.apache.pekko.stream.scaladsl.compression.ZstdDictionary]):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#zstd(int,java.util.Optional)" }
@apidoc[Compression.zstd](stream.*.Compression$) { scala="#zstd(level:Int,dictionary:Option[org.apache.pekko.stream.scaladsl.compression.ZstdDictionary],autoFlush:Boolean):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#zstd(int,java.util.Optional,boolean)" }

## Description

Creates a flow that zstd-compresses a stream of ByteStrings. Note that the compressor
will SYNC_FLUSH after every @apidoc[util.ByteString] so that it is guaranteed that every @apidoc[util.ByteString]
coming out of the flow can be fully decompressed without waiting for additional data. This may
come at a compression performance cost for very small chunks.

Use the `zstd(level, dictionary)` overload to control the compression level and, optionally, to compress with a
dictionary. Output compressed with a dictionary can only be decompressed with the same dictionary, which
`zstdDecompress` does not support.

The `zstd(level, dictionary, autoFlush)` overload adds an `autoFlush` parameter. If `autoFlush` is `true` (the default
for the other overloads), the compressor flushes after every single element in the stream. If it is `false`, the
compressor only emits output when the compression algorithm produces it, which can improve the compression ratio for
small chunks, but an emitted `ByteString` may not be fully decompressible until more data arrives.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the compression algorithm produces output for the received `ByteString`

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
