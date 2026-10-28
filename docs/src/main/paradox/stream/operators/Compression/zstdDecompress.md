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
@apidoc[Compression.zstdDecompress](stream.*.Compression$) { scala="#zstdDecompress(settings:org.apache.pekko.stream.ZstdDecompressionSettings):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#zstdDecompress(org.apache.pekko.stream.ZstdDecompressionSettings)" }

## Description

Creates a flow that zstd-decompresses a stream of ByteStrings. If the input is truncated or corrupt this operator
fails with a `com.github.luben.zstd.ZstdException`.

The `zstdDecompress(settings)` overload takes a @apidoc[ZstdDecompressionSettings](stream.ZstdDecompressionSettings), which sets the maximum chunk size
and, with `withDictionary`, a dictionary to decompress data that was compressed with one. It must be the same
dictionary (including any offset and length) that was used for compression; the dictionary level only applies to
compression and is ignored.

By default, zstd accepts frames with a window of up to 128 MiB (a window log of 27), and the decompressor allocates
the window buffer from the frame header before any data is decompressed. When decompressing untrusted input, use
`withWindowLogMax` to reject frames with a larger window, for example a window log of 23 (8 MiB), the limit that
[RFC 9659](https://www.rfc-editor.org/rfc/rfc9659) sets for the HTTP `zstd` content-coding. `withWindowLogMax`
requires zstd-jni 1.5.7-25 or later; with an older version, the operator fails when it is materialized.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the decompression algorithm produces output for the received `ByteString` (the emitted `ByteString` is of `maxBytesPerChunk` maximum length)

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
