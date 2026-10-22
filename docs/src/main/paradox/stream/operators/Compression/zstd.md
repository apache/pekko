# Compression.zstd

Creates a flow that zstd-compresses a stream of ByteStrings.

@ref[Compression operators](../index.md#compression-operators)

## Signature

@apidoc[Compression.zstd](stream.*.Compression$) { scala="#zstd:org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#zstd()" }

## Dependency

The zstd operators use [zstd-jni](https://github.com/luben/zstd-jni), an optional dependency of Pekko Streams
that has to be added explicitly:

@@dependency[sbt,Maven,Gradle] {
  group=com.github.luben
  artifact=zstd-jni
  version="$zstd_jni_version$"
}

## Description

Creates a flow that zstd-compresses a stream of ByteStrings. Note that the compressor
will SYNC_FLUSH after every @apidoc[util.ByteString] so that it is guaranteed that every @apidoc[util.ByteString]
coming out of the flow can be fully decompressed without waiting for additional data. This may
come at a compression performance cost for very small chunks.

Use the overload method to control the compression level.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the compression algorithm produces output for the received `ByteString`

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
