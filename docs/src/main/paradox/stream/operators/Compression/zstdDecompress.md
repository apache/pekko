# Compression.zstdDecompress

Creates a flow that zstd-decompresses a stream of ByteStrings.

@ref[Compression operators](../index.md#compression-operators)

## Signature

@apidoc[Compression.zstdDecompress](stream.*.Compression$) { scala="#zstdDecompress(maxBytesPerChunk:Int):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#zstdDecompress(int)" }

## Dependency

The zstd operators use [zstd-jni](https://github.com/luben/zstd-jni), an optional dependency of Pekko Streams
that has to be added explicitly:

@@dependency[sbt,Maven,Gradle] {
  group=com.github.luben
  artifact=zstd-jni
  version="$zstd_jni_version$"
}

## Description

Creates a flow that zstd-decompresses a stream of ByteStrings. If the input is truncated, uses invalid
compression method or is invalid (failed CRC checks) this operator fails with a `com.github.luben.zstd.ZstdException`.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the decompression algorithm produces output for the received `ByteString` (the emitted `ByteString` is of `maxBytesPerChunk` maximum length)

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
