/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import org.apache.pekko
import pekko.stream.{ SinkRef, SourceRef }
import pekko.stream.impl.streamref.{ SinkRefStageImpl, SourceRefStageImpl }
import pekko.util.OptionVal

/**
 * Factories for creating stream refs.
 */
object StreamRefs {

  /**
   * A local [[Sink]] which materializes a [[SourceRef]] which can be used by other streams (including remote ones),
   * to consume data from this local stream, as if they were attached directly in place of the local Sink.
   *
   * Adheres to [[pekko.stream.StreamRefAttributes]].
   *
   * See more detailed documentation on [[SourceRef]].
   */
  def sourceRef[T](): Sink[T, SourceRef[T]] =
    Sink.fromGraph(new SinkRefStageImpl[T](OptionVal.None))

  /**
   * A local [[Source]] which materializes a [[SinkRef]] which can be used by other streams (including remote ones),
   * to publish data to this local stream, as if they were attached directly in place of the local Source.
   *
   * Adheres to [[pekko.stream.StreamRefAttributes]].
   *
   * See more detailed documentation on [[SinkRef]].
   */
  def sinkRef[T](): Source[T, SinkRef[T]] =
    Source.fromGraph(new SourceRefStageImpl[T](OptionVal.None))
}
