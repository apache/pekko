<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Sink.fromMaterializer

Defer the creation of a `Sink` until materialization and access `Materializer` and `Attributes`

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.fromMaterializer](Sink$) { scala="#fromMaterializer[T,M](factory:(org.apache.pekko.stream.Materializer,org.apache.pekko.stream.Attributes)=&gt;org.apache.pekko.stream.scaladsl.Sink[T,M]):org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[M]]" java="#fromMaterializer(org.apache.pekko.japi.function.Function2)" }

## Description

Typically used when access to materializer is needed to run a different stream during the construction of a sink.
Can also be used to access the underlying `ActorSystem` from `Materializer`.
