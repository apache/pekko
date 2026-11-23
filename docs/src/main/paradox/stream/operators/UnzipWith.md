<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# UnzipWith

Splits each element of input into multiple downstreams using a function

@ref[Fan-out operators](index.md#fan-out-operators)

## Signature

@apidoc[stream.*.UnzipWith$]

## Description

Splits each element of input into multiple downstreams using a function

## Reactive Streams semantics

@@@div { .callout }

**emits** when all of the outputs stops backpressuring and there is an input element available

**backpressures** when any of the outputs backpressures

**completes** when upstream completes

@@@


