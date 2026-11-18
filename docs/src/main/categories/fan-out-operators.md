<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

These have one input and multiple outputs. They might route the elements between different outputs, or emit elements on
multiple outputs at the same time.

There is a number of fan-out operators for which currently no 'fluent' is API available. To use those you will have to use the @ref[Graph DSL](../stream-graphs.md#constructing-graphs).
