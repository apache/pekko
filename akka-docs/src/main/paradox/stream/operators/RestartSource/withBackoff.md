# RestartSource.withBackoff

Wrap the given @apidoc[Source] with a @apidoc[Source] that will restart it when it fails or completes using an exponential backoff.

@ref[Error handling](../index.md#error-handling)

## Signature

@apidoc[RestartSource.withBackoff](RestartSource$) { scala="#withBackoff[T](settings:org.apache.pekko.stream.RestartSettings)(sourceFactory:()=&gt;org.apache.pekko.stream.scaladsl.Source[T,_]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#withBackoff(org.apache.pekko.stream.RestartSettings,org.apache.pekko.japi.function.Creator)" }

## Description

Wrap the given @apidoc[Source] with a @apidoc[Source] that will restart it when it completes or fails using exponential backoff.
The backoff resets back to `minBackoff` if there hasn't been a restart within `maxRestartsWithin`  (which defaults to `minBackoff`).

This @apidoc[Source] will not emit a complete or fail as long as maxRestarts is not reached, since the completion
or failure of the wrapped @apidoc[Source] is handled by restarting it. The wrapped @apidoc[Source] can however be cancelled
by cancelling this @apidoc[Source]. When that happens, the wrapped @apidoc[Source], if currently running, will be cancelled,
and it will not be restarted.
This can be triggered simply by the downstream cancelling, or externally by introducing a @ref[KillSwitch](../../stream-dynamic.md#controlling-stream-completion-with-killswitch) right
after this @apidoc[Source] in the graph.

This uses the same exponential backoff algorithm as @apidoc[BackoffOpts$].

See also: 
 
* @ref:[RestartSource.onFailuresWithBackoff](../RestartSource/onFailuresWithBackoff.md)
* @ref:[RestartFlow.onFailuresWithBackoff](../RestartFlow/onFailuresWithBackoff.md)
* @ref:[RestartFlow.withBackoff](../RestartFlow/withBackoff.md)
* @ref:[RestartSink.withBackoff](../RestartSink/withBackoff.md)

## Reactive Streams semantics

@@@div { .callout }

**emits** when the wrapped source emits

**backpressures** during backoff and when downstream backpressures

**completes** when `maxRestarts` are reached within the given time limit

**cancels** when downstream cancels

@@@
