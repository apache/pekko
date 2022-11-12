# Sink.preMaterialize

Materializes this Sink, immediately returning (1) its materialized value, and (2) a new Sink that can be consume elements 'into' the pre-materialized one.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.preMaterialize](Sink) { scala="#preMaterialize()(implicitmaterializer:org.apache.pekko.stream.Materializer):(Mat,org.apache.pekko.stream.scaladsl.Sink[In,org.apache.pekko.NotUsed])" java="#preMaterialize(org.apache.pekko.actor.ClassicActorSystemProvider)" java="#preMaterialize(org.apache.pekko.stream.Materializer)" }


## Description

Materializes this Sink, immediately returning (1) its materialized value, and (2) a new Sink that can be consume elements 'into' the pre-materialized one. Useful for when you need a materialized value of a Sink when handing it out to someone to materialize it for you.

