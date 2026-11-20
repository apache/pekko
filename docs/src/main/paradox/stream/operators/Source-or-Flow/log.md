<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# log

Log elements flowing through the stream as well as completion and erroring.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.log](Source) { scala="#log(name:String,extract:Out=&gt;Any)(implicitlog:org.apache.pekko.event.LoggingAdapter):FlowOps.this.Repr[Out]" java="#log(java.lang.String)" }
@apidoc[Flow.log](Flow) { scala="#log(name:String,extract:Out=&gt;Any)(implicitlog:org.apache.pekko.event.LoggingAdapter):FlowOps.this.Repr[Out]" java="#log(java.lang.String)" }

## Description

Log elements flowing through the stream as well as completion and erroring. By default element and
completion signals are logged on debug level, and errors are logged on Error level.
This can be changed by calling @scala[`Attributes.logLevels(...)`] @java[`Attributes.createLogLevels(...)`] on the given Flow.

See also @ref:[logWithMarker](logWithMarker.md).

## Example

Scala
:   @@snip [SourceOrFlow.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Log.scala) { #log }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #log }

## Reactive Streams semantics 

@@@div { .callout }

**emits** when upstream emits

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
