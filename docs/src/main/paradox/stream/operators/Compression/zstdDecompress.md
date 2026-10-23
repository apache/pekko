# Compression.zstdDecompress

Creates a flow that zstd-decompresses a stream of ByteStrings.

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

@apidoc[Compression.zstdDecompress](stream.*.Compression$) { scala="#zstdDecompress(maxBytesPerChunk:Int):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#zstdDecompress(int)" }
@apidoc[Compression.zstdDecompress](stream.*.Compression$) { scala="#zstdDecompress(maxBytesPerChunk:Int,dictionary:Option[org.apache.pekko.stream.scaladsl.compression.ZstdDictionary]):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#zstdDecompress(int,java.util.Optional)" }

## Description

Creates a flow that zstd-decompresses a stream of ByteStrings. If the input is truncated or corrupt this operator
fails with a `com.github.luben.zstd.ZstdException`.

Use the `zstdDecompress(maxBytesPerChunk, dictionary)` overload to decompress data that was compressed with a
dictionary. It must be the same dictionary (including any offset and length) that was used for compression; the
dictionary level only applies to compression and is ignored.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the decompression algorithm produces output for the received `ByteString` (the emitted `ByteString` is of `maxBytesPerChunk` maximum length)

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
