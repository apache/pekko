# Flow.flattenOptional

Collect the value of `Optional` from all the elements passing through this flow , empty `Optional` is filtered out.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Flow.flattenOptional](Flow$) { java="#flattenOptional()" }


## Description

Creates a `Flow` that emits the value of each present @javadoc[Optional](java.util.Optional) element passing through it,
while empty `Optional` elements are filtered out (dropped).

## Reactive Streams semantics

@@@div { .callout }

**Emits when** the current @javadoc[Optional](java.util.Optional)'s value is present.

**Backpressures when** the value of the current @javadoc[Optional](java.util.Optional) is present and downstream backpressures.

**Completes when** upstream completes.

**Cancels when** downstream cancels.
@@@

