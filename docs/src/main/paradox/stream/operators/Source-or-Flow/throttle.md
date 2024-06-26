# throttle

Limit the throughput to a specific number of elements per time unit, or a specific total cost per time unit, where a function has to be provided to calculate the individual cost of each element.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.throttle](Source) { scala="#throttle(cost:Int,per:scala.concurrent.duration.FiniteDuration,maximumBurst:Int,costCalculation:Out=&gt;Int,mode:org.apache.pekko.stream.ThrottleMode):FlowOps.this.Repr[Out]" java="#throttle(int,java.time.Duration,int,org.apache.pekko.japi.function.Function,org.apache.pekko.stream.ThrottleMode)" }
@apidoc[Flow.throttle](Flow) { scala="#throttle(cost:Int,per:scala.concurrent.duration.FiniteDuration,maximumBurst:Int,costCalculation:Out=&gt;Int,mode:org.apache.pekko.stream.ThrottleMode):FlowOps.this.Repr[Out]" java="#throttle(int,java.time.Duration,int,org.apache.pekko.japi.function.Function,org.apache.pekko.stream.ThrottleMode)" }

## Description

Limit the throughput to a specific number of elements per time unit, or a specific total cost per time unit, where
a function has to be provided to calculate the individual cost of each element.

The throttle operator combines well with the @ref[`queue`](./../Source/queue.md) operator to adapt the speeds on both ends of the `queue`-`throttle` pair.

See also @ref:[Buffers and working with rate](../../stream-rate.md) for related operators.

## Example

Imagine the server end of a streaming platform. When a client connects and request a video content, the server 
should return the content. Instead of serving a complete video as fast as bandwidth allows, `throttle` can be used
to limit the network usage to 24 frames per second (let's imagine this streaming platform stores frames, not bytes).

Scala
:   @@snip [Throttle.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Throttle.scala) { #throttle }

Java
:   @@snip [Throttle.java](/docs/src/test/java/jdocs/stream/operators/sourceorflow/Throttle.java) { #throttle }

The problem in the example above is that when there's a network hiccup, the video playback will interrupt. It can be
improved by sending more content than the necessary ahead of time and let the client buffer that. So, `throttle` can be used 
to burst the first 30 seconds and then send a constant of 24 frames per second. This way, when a request comes in
a good chunk of content will be downloaded and after that the server will activate the throttling.

Scala
:   @@snip [Throttle.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Throttle.scala) { #throttle-with-burst }

Java
:   @@snip [Throttle.java](/docs/src/test/java/jdocs/stream/operators/sourceorflow/Throttle.java) { #throttle-with-burst }

The extra argument to set the `ThrottleMode` to `shaping` tells throttle to make pauses to avoid exceeding 
the maximum rate. Alternatively we could set the `ThrottleMode` to `enforcing` to cause a stream failure when upstream is faster
than the throttle rate.   

The examples above don't cover all the parameters supported by `throttle` (e.g. `cost`-based throttling). See the 
@apidoc[api documentation](Flow) { scala="#throttle(cost:Int,per:scala.concurrent.duration.FiniteDuration,maximumBurst:Int,costCalculation:Out=&gt;Int,mode:org.apache.pekko.stream.ThrottleMode):FlowOps.this.Repr[Out]" java="#throttle(int,java.time.Duration,int,org.apache.pekko.japi.function.Function,org.apache.pekko.stream.ThrottleMode)" }
for all the details.

## Reactive Streams semantics

@@@div { .callout }

**emits** when upstream emits an element and configured time per each element elapsed

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@

