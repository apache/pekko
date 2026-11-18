<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# zipLatest

Combines elements from each of multiple sources into @scala[tuples] @java[*Pair*] and passes the @scala[tuples] @java[pairs] downstream, picking always the latest element of each.

@ref[Fan-in operators](../index.md#fan-in-operators)

## Signature

@apidoc[Source.zipLatest](Source) { scala="#zipLatest[U](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[U],_]):FlowOps.this.Repr[(Out,U)]" java="#zipLatest(org.apache.pekko.stream.Graph)" }
@apidoc[Flow.zipLatest](Flow) { scala="#zipLatest[U](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[U],_]):FlowOps.this.Repr[(Out,U)]" java="#zipLatest(org.apache.pekko.stream.Graph)" }


## Description

Combines elements from each of multiple sources into @scala[tuples] @java[*Pair*] and passes the @scala[tuples] @java[pairs] downstream, picking always the latest element of each.

No element is emitted until at least one element from each Source becomes available.
 
## Reactive Streams semantics

@@@div { .callout }

**emits** when all of the inputs have at least an element available, and then each time an element becomes
          available on either of the inputs

**backpressures** when downstream backpressures

**completes** when any upstream completes

**cancels** when downstream cancels

@@@

