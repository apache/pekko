# Source.lazily

Deprecated by @ref:[`Source.lazySource`](lazySource.md).

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.lazily](Source$) { scala="#lazily[T,M](create:()=&gt;org.apache.pekko.stream.scaladsl.Source[T,M]):org.apache.pekko.stream.scaladsl.Source[T,scala.concurrent.Future[M]]" java="#lazily(org.apache.pekko.japi.function.Creator)" }


## Description

`lazily` is deprecated, please use @ref:[lazySource](lazySource.md) instead.

Defers creation and materialization of a `Source` until there is demand.

## Reactive Streams semantics

@@@div { .callout }

**emits** depends on the wrapped `Source`

**completes** depends on the wrapped `Source`

@@@

