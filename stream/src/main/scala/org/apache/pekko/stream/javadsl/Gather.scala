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

import org.apache.pekko.annotation.DoNotInherit
import org.apache.pekko.japi.function

/**
 * Collector passed to [[Gatherer]] for emitting output elements.
 *
 * The collector is only valid while the current [[Gatherer]] callback is running.
 * Emitted elements MUST NOT be `null`.
 *
 * @since 1.3.0
 */
@DoNotInherit
trait GatherCollector[-Out] extends function.Procedure[Out] {
  def push(elem: Out): Unit

  final override def apply(param: Out): Unit = push(param)
}

/**
 * A stateful gatherer for the `gather` operator.
 *
 * A new gatherer instance is created for each materialization and on each supervision restart.
 * It can keep mutable state in fields or via captured variables.
 *
 * @since 1.3.0
 */
@FunctionalInterface
trait Gatherer[-In, Out] extends function.Procedure2[In, GatherCollector[Out]] {

  /**
   * Called once whenever the stage terminates or restarts: on upstream completion, upstream failure,
   * downstream cancellation, abrupt stage termination, or when the stage is restarted due to supervision.
   *
   * Elements pushed to the collector are emitted only on upstream completion, upstream failure,
   * or supervision restart. They are ignored on downstream cancellation and abrupt termination.
   */
  def onComplete(collector: GatherCollector[Out]): Unit = ()
}

/** Factory methods for [[Gatherer]]. */
object Gatherers {

  /**
   * Creates a specialized `Gatherer` for one-to-one transformations (exactly one output per input).
   *
   * This variant avoids the overhead of the `GatherCollector` indirection and achieves the
   * same performance as the native `map` operator while still supporting mutable state and
   * the `onComplete` callback.
   *
   * @param f the one-to-one transformation function
   * @since 1.3.0
   */
  def oneToOne[In, Out](f: function.Function[In, Out]): Gatherer[In, Out] =
    new OneToOneGathererImpl[In, Out](f)

  /**
   * Creates a specialized `Gatherer` for one-to-one transformations with an `onComplete` callback.
   *
   * @param f the one-to-one transformation function
   * @param onComplete callback invoked when the stage terminates or restarts
   * @since 1.3.0
   */
  def oneToOne[In, Out](f: function.Function[In, Out], onComplete: function.Effect): Gatherer[In, Out] =
    new OneToOneGathererImpl[In, Out](f, onComplete)

  /**
   * A specialized [[Gatherer]] for one-to-one transformations that avoids the `GatherCollector` overhead.
   *
   * @since 1.3.0
   */
  @DoNotInherit
  trait OneToOneGatherer[In, Out] extends Gatherer[In, Out] {
    def applyOne(in: In): Out

    final override def apply(in: In, collector: GatherCollector[Out]): Unit =
      collector.push(applyOne(in))
  }

  private final class OneToOneGathererImpl[In, Out](
      f: function.Function[In, Out],
      onCompleteCallback: function.Effect = null)
      extends OneToOneGatherer[In, Out] {
    override def applyOne(in: In): Out = f.apply(in)
    override def onComplete(@annotation.nowarn collector: GatherCollector[Out]): Unit =
      if (onCompleteCallback != null) onCompleteCallback.apply()
  }
}
