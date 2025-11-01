# Source.fromOption

Create a `Source` from an @scala[`Option[T]`] @java[`Optional<T>`] value, emitting the value if it is present.

@ref[Source operators](../index.md#source-operators)


## Signature

@apidoc[Source.fromOption](Source$) { }


## Description

Create a `Source` from an @scala[`Option[T]`] @java[`Optional<T>`] value, emitting the value if it is present.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the value is present

**completes** afterwards

@@@

