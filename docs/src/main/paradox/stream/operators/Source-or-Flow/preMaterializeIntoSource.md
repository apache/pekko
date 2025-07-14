# preMaterializeIntoSource

Materializes this Graph, immediately returning its materialized values into a new Source.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.preMaterializeIntoSource](Source) { scala="#preMaterializeIntoSource[Mat2](sink:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SinkShape[Out],scala.concurrent.Future[Mat2]]):org.apache.pekko.stream.scaladsl.Source[Mat2,scala.concurrent.Future[org.apache.pekko.NotUsed]]" java="#preMaterializeIntoSource(org.apache.pekko.stream.Graph)" }
@apidoc[Flow.preMaterializeIntoSource](Flow) { scala="#preMaterializeIntoSource[Mat1,Mat2](source:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[In],Mat1],sink:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SinkShape[Out],scala.concurrent.Future[Mat2]]):org.apache.pekko.stream.scaladsl.Source[Mat2,scala.concurrent.Future[org.apache.pekko.NotUsed]]" java="#preMaterialize(org.apache.pekko.actor.ClassicActorSystemProvider)" java="#preMaterializeIntoSource(org.apache.pekko.stream.Graph,org.apache.pekko.stream.Graph)" }


## Description

Materializes this Graph, immediately returning its materialized values into a new Source.
