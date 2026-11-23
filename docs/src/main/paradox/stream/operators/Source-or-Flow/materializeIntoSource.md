<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements. See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# materializeIntoSource

Materializes this Graph, immediately returning its materialized values into a new Source.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.materializeIntoSource](Source) { scala="#materializeIntoSource[Mat2](sink:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SinkShape[Out],scala.concurrent.Future[Mat2]]):org.apache.pekko.stream.scaladsl.Source[Mat2,scala.concurrent.Future[org.apache.pekko.NotUsed]]" java="#materializeIntoSource(org.apache.pekko.stream.Graph)" }
@apidoc[Flow.materializeIntoSource](Flow) { scala="#materializeIntoSource[Mat1,Mat2](source:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[In],Mat1],sink:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SinkShape[Out],scala.concurrent.Future[Mat2]]):org.apache.pekko.stream.scaladsl.Source[Mat2,scala.concurrent.Future[org.apache.pekko.NotUsed]]" java="#materializeIntoSource(org.apache.pekko.stream.Graph,org.apache.pekko.stream.Graph)" }


## Description

Materializes this Graph, immediately returning its materialized values into a new Source.
