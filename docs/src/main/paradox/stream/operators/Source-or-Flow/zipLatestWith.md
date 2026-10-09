# zipLatestWith

Combines elements from multiple sources through a `combine` function and passes the returned value downstream, picking always the latest element of each.

@ref[Fan-in operators](../index.md#fan-in-operators)

## Signature

@apidoc[Source.zipLatestWith](Source) { scala="#zipLatestWith[Out2,Out3](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[Out2],_])(combine:(Out,Out2)=&gt;Out3):FlowOps.this.Repr[Out3]" java="#zipLatestWith(org.apache.pekko.stream.Graph,org.apache.pekko.japi.function.Function2)" }
@apidoc[Flow.zipLatestWith](Flow) { scala="#zipLatestWith[Out2,Out3](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[Out2],_])(combine:(Out,Out2)=&gt;Out3):FlowOps.this.Repr[Out3]" java="#zipLatestWith(org.apache.pekko.stream.Graph,org.apache.pekko.japi.function.Function2)" }
@apidoc[Source.zipLatestWith](Source) { scala="#zipLatestWith[Out2,Out3](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[Out2],_],eagerComplete:Boolean)(combine:(Out,Out2)=&gt;Out3):FlowOps.this.Repr[Out3]" java="#zipLatestWith(org.apache.pekko.stream.Graph,boolean,org.apache.pekko.japi.function.Function2)" }
@apidoc[Flow.zipLatestWith](Flow) { scala="#zipLatestWith[Out2,Out3](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[Out2],_],eagerComplete:Boolean)(combine:(Out,Out2)=&gt;Out3):FlowOps.this.Repr[Out3]" java="#zipLatestWith(org.apache.pekko.stream.Graph,boolean,org.apache.pekko.japi.function.Function2)" }


## Description

Combines elements from each of multiple sources through a `combine` function and passes the returned value downstream, picking always the latest element of each.

No element is emitted until at least one element from each Source becomes available. Whenever a new
element appears, the `combine` function is invoked with the new element and the last seen element of the other stream.

By default the stream completes as soon as any upstream completes. Use the overload with `eagerComplete` set to `false`
to keep running until all upstreams have completed (the stream still completes immediately if an upstream
completes before it has emitted any element).

## Reactive Streams semantics

@@@div { .callout }

**emits** all of the inputs have at least an element available, and then each time an element becomes
          available on either of the inputs

**backpressures** when downstream backpressures

**completes** when any upstream completes, or when all upstreams complete if `eagerComplete` is `false`

**cancels** when downstream cancels

@@@

