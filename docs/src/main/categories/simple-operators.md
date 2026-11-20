<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

These operators can transform the rate of incoming elements since there are operators that emit multiple elements for a
single input (e.g. `mapConcat`) or consume multiple elements before emitting one output (e.g. `filter`).
However, these rate transformations are data-driven, i.e. it is the incoming elements that define how the
rate is affected. This is in contrast with [detached operators](#backpressure-aware-operators) which can change their processing behavior
depending on being backpressured by downstream or not.