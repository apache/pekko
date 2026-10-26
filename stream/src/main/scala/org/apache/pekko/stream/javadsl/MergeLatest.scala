/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl

import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.stream.{ scaladsl, UniformFanInShape }
import pekko.stream.stage.GraphStage

/**
 * MergeLatest joins elements from N input streams into stream of lists of size N.
 * i-th element in list is the latest emitted element from i-th input stream.
 * MergeLatest emits the latest values from all input streams after each stream has emitted at least one element.
 * By default, updates are conflated while downstream is back-pressured. Set
 * `pekko.stream.materializer.merge-latest.mode = legacy` to retain the previous behavior of buffering every update.
 *
 * '''Emits when''' an input element is available and each input has emitted at least one element from stream start;
 * in the default mode, at most the latest combination is retained while downstream is back-pressured
 *
 * '''Completes when''' all upstreams complete (eagerClose=false) or one upstream completes (eagerClose=true)
 *
 * '''Cancels when''' downstream cancels
 */
object MergeLatest {

  /**
   * Create a new `MergeLatest` with the specified number of input ports.
   *
   * @param inputPorts number of input ports
   * @param eagerComplete if true, the merge latest will complete as soon as one of its inputs completes.
   */
  def create[T](inputPorts: Int, eagerComplete: Boolean): GraphStage[UniformFanInShape[T, java.util.List[T]]] =
    new scaladsl.MergeLatest[T, java.util.List[T]](inputPorts, eagerComplete)(x => x.toList.asJava)

  /**
   * Create a new `MergeLatest` with the specified number of input ports.
   *
   * @param inputPorts number of input ports
   */
  def create[T](inputPorts: Int): GraphStage[UniformFanInShape[T, java.util.List[T]]] = create(inputPorts, false)
}
