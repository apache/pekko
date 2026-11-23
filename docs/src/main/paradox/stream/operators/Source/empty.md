<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Source.empty

Complete right away without ever emitting any elements.

@ref[Source operators](../index.md#source-operators)

@ref:[`Source.never`](never.md) a source which emits nothing and never completes.

## Signature

@apidoc[Source.empty](Source$) { scala="#empty[T]:org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#empty()" java="#empty(java.lang.Class)" }


## Description

Complete right away without ever emitting any elements. Useful when you have to provide a source to
an API but there are no elements to emit.

## Reactive Streams semantics

@@@div { .callout }

**emits** never

**completes** directly

@@@

