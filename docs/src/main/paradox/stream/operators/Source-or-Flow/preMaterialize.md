# preMaterialize

Materializes this Graph, immediately returning (1) its materialized value, and (2) a new pre-materialized Graph.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.preMaterialize](Source) { scala="#preMaterialize()(implicitmaterializer:org.apache.pekko.stream.Materializer):(Mat,org.apache.pekko.stream.scaladsl.Source[Out,org.apache.pekko.NotUsed])" java="#preMaterialize(org.apache.pekko.actor.ClassicActorSystemProvider)" java="#preMaterialize(org.apache.pekko.stream.Materializer)" }
@apidoc[Flow.preMaterialize](Flow) { scala="#preMaterialize()(implicitmaterializer:org.apache.pekko.stream.Materializer):(Mat,org.apache.pekko.stream.scaladsl.Flow[Int,Out,org.apache.pekko.NotUsed])" java="#preMaterialize(org.apache.pekko.actor.ClassicActorSystemProvider)" java="#preMaterialize(org.apache.pekko.stream.Materializer)" }


## Description

Materializes this Graph, immediately returning (1) its materialized value, and (2) a new pre-materialized Graph.

