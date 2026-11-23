<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Unzip

Takes a stream of two element tuples and unzips the two elements into two different downstreams.

@ref[Fan-out operators](index.md#fan-out-operators)

## Signature

@apidoc[stream.*.Unzip$]

## Description

Takes a stream of two element tuples and unzips the two elements into two different downstreams.

## Reactive Streams semantics

@@@div { .callout }

**emits** when all of the outputs stops backpressuring and there is an input element available

**backpressures** when any of the outputs backpressures

**completes** when upstream completes

@@@

