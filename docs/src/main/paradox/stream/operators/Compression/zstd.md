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
@apidoc[Compression.zstd](stream.*.Compression$) { scala="#zstd(settings:org.apache.pekko.stream.ZstdCompressionSettings):org.apache.pekko.stream.scaladsl.Flow[org.apache.pekko.util.ByteString,org.apache.pekko.util.ByteString,org.apache.pekko.NotUsed]" java="#zstd(org.apache.pekko.stream.ZstdCompressionSettings)" }

## Description

Creates a flow that zstd-compresses a stream of ByteStrings. Note that the compressor
will SYNC_FLUSH after every @apidoc[util.ByteString] so that it is guaranteed that every @apidoc[util.ByteString]
coming out of the flow can be fully decompressed without waiting for additional data. This may
come at a compression performance cost for very small chunks.

Use the `zstd(level, dictionary)` overload to control the compression level and, optionally, to compress with a
dictionary. Output compressed with a dictionary can only be decompressed with the same dictionary, see
@ref[zstdDecompress](zstdDecompress.md).

By default, zstd auto-detects the type of the dictionary: bytes that start with the zstd dictionary magic number
(`0xEC30A437`) are parsed as a formatted dictionary, as produced by `zstd --train`, and any other bytes are used as
raw content. Use `ZstdDictionary.withContentType` with a @apidoc[ZstdDictionaryContentType](stream.ZstdDictionaryContentType)
to always use the bytes as raw content (`RAW_CONTENT`), for example when the dictionary can be any data, as with the
HTTP `dcz` content-coding of [RFC 9842](https://www.rfc-editor.org/rfc/rfc9842), or to require a formatted
dictionary (`FULL_DICT`). Content types other than `AUTO` require zstd-jni 1.5.7-26 or later; with an older version,
the operator fails when the dictionary is loaded.

The `zstd(settings)` overload takes a @apidoc[ZstdCompressionSettings](stream.ZstdCompressionSettings), which sets the level and dictionary as well as:

* `withAutoFlush`: if `true` (the default), the compressor flushes after every single element in the stream. If it is
  `false`, the compressor only emits output when the compression algorithm produces it, which can improve the
  compression ratio for small chunks, but an emitted `ByteString` may not be fully decompressible until more data
  arrives.
* `withWindowLog`: limits the window size to `2^windowLog` bytes. The decompressor has to buffer a window of this size,
  and since the size of a stream is not known up front, a frame declares the default window of its compression level,
  which is 128 MiB (`windowLog` 27) for the highest levels. Set `windowLog` when the output is decompressed by a peer
  that limits the window, for example to 23 (8 MiB) for the HTTP `zstd` content-coding, as required by
  [RFC 9659](https://www.rfc-editor.org/rfc/rfc9659). The default of 0 uses the default of the compression level.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the compression algorithm produces output for the received `ByteString`

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
