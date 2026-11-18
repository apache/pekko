<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Source.failed

Fail directly with a user specified exception.

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.failed](Source$) { scala="#failed[T](cause:Throwable):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#failed(java.lang.Throwable)" }


## Description

Fail directly with a user specified exception.

## Reactive Streams semantics

@@@div { .callout }

**emits** never

**completes** fails the stream directly with the given exception

@@@

