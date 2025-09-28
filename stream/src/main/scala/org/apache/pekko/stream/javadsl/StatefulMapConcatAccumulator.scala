/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.javadsl

import java.util.Collections

import scala.annotation.unchecked.uncheckedVariance

import org.apache.pekko
import pekko.japi.function

/**
 * A special accumulator for `StatefulMapConcat` operator that allows to emit elements when the upstream has completed.
 *
 * @since 1.2.0
 */
@FunctionalInterface
trait StatefulMapConcatAccumulator[-In, Out] extends function.Function[In, java.lang.Iterable[Out]] {

  /**
   * Called once the upstream has been completed, optional elements can be emitted, by default none.
   */
  def onComplete(): java.lang.Iterable[Out @uncheckedVariance] = Collections.emptyList[Out]()
}

/**
 * A factory for creating `StatefulMapConcatAccumulator` instances.
 *
 * @since 1.2.0
 */
@FunctionalInterface
trait StatefulMapConcatAccumulatorFactory[-In, +Out] {

  /**
   * Creates a new `StatefulMapConcatAccumulator` instance.
   */
  def accumulator(): StatefulMapConcatAccumulator[In, Out @uncheckedVariance]
}
