# fromMaterializer

Defer the creation of a `Source/Flow` until materialization and access `Materializer` and `Attributes`

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.fromMaterializer](Source$) { scala="#fromMaterializer[T,M](factory:(org.apache.pekko.stream.Materializer,org.apache.pekko.stream.Attributes)=&gt;org.apache.pekko.stream.scaladsl.Source[T,M]):org.apache.pekko.stream.scaladsl.Source[T,scala.concurrent.Future[M]]" java="#fromMaterializer(java.util.function.BiFunction)" }
@apidoc[Flow.fromMaterializer](Flow$) { scala="#fromMaterializer[T,U,M](factory:(org.apache.pekko.stream.Materializer,org.apache.pekko.stream.Attributes)=&gt;org.apache.pekko.stream.scaladsl.Flow[T,U,M]):org.apache.pekko.stream.scaladsl.Flow[T,U,scala.concurrent.Future[M]]" java="#fromMaterializer(java.util.function.BiFunction)" }


## Description

Typically used when access to materializer is needed to run a different stream during the construction of a source/flow.
Can also be used to access the underlying `ActorSystem` from `Materializer`.
