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

package org.apache.pekko.stream.scaladsl

import org.apache.pekko.annotation.DoNotInherit

/**
 * Collector passed to [[Gatherer]] for emitting output elements.
 *
 * The collector is only valid while the current [[Gatherer]] callback is running.
 *
 * @since 2.0.0
 */
@DoNotInherit
trait GatherCollector[-Out] {
  def push(elem: Out): Unit
}

/**
 * A stateful gatherer for the `gather` operator.
 *
 * A new gatherer instance is created for each materialization and on each supervision restart.
 * It can keep mutable state in fields or closures.
 *
 * @since 2.0.0
 */
@FunctionalInterface
trait Gatherer[-In, +Out] {
  def apply(in: In, collector: GatherCollector[Out]): Unit

  /**
   * Called once whenever the stage terminates or restarts: on upstream completion, upstream failure,
   * downstream cancellation, abrupt stage termination, or when the stage is restarted due to supervision.
   *
   * Elements pushed to the collector are emitted only on upstream completion, upstream failure,
   * or supervision restart. They are ignored on downstream cancellation and abrupt termination.
   */
  def onComplete(collector: GatherCollector[Out]): Unit = ()
}

/**
 * INTERNAL API
 */
@DoNotInherit
@FunctionalInterface
private[stream] trait OneToOneGatherer[-In, +Out] extends Gatherer[In, Out] {
  def applyOne(in: In): Out

  final override def apply(in: In, collector: GatherCollector[Out]): Unit =
    collector.push(applyOne(in))
}
