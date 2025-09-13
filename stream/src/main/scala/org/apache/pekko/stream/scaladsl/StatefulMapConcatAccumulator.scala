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

import org.apache.pekko
import pekko.japi.function
import pekko.stream.{ javadsl, scaladsl }

/**
 * A special accumulator for `StatefulMapConcat` operator that allows to emit elements when the upstream has completed.
 *
 * @since 1.2.0
 */
@FunctionalInterface
trait StatefulMapConcatAccumulator[-In, +Out] extends (In => IterableOnce[Out]) {

  /**
   * Called once the upstream has been completed, optional elements can be emitted, by default none.
   */
  def onComplete(): IterableOnce[Out] = Nil
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
  def accumulator(): StatefulMapConcatAccumulator[In, Out]
}

private[pekko] object StatefulMapConcatAccumulatorFactory {
  implicit final class StatefulMapConcatAccumulatorFactoryFromJavaApi[In, Out](
      val factory: javadsl.StatefulMapConcatAccumulatorFactory[In, Out]) extends AnyVal {

    def asScala: StatefulMapConcatAccumulatorFactory[In, Out] = new StatefulMapConcatAccumulatorFactory[In, Out] {
      final override def accumulator(): StatefulMapConcatAccumulator[In, Out] =
        new StatefulMapConcatAccumulator[In, Out] {
          private val accumulator = factory.accumulator()
          import scala.jdk.CollectionConverters._
          final override def apply(in: In): IterableOnce[Out] = accumulator(in).asScala
          final override def onComplete(): IterableOnce[Out] = accumulator.onComplete().asScala
        }
    }
  }

  implicit final class StatefulMapConcatAccumulatorFactoryFromCreator[In, Out](
      val f: function.Creator[function.Function[In, java.lang.Iterable[Out]]]) extends AnyVal {

    def asFactory: StatefulMapConcatAccumulatorFactory[In, Out] = new StatefulMapConcatAccumulatorFactory[In, Out] {
      override def accumulator(): StatefulMapConcatAccumulator[In, Out] =
        new scaladsl.StatefulMapConcatAccumulator[In, Out] {
          private val accumulator = f.create()
          import scala.jdk.CollectionConverters._
          final override def apply(in: In): IterableOnce[Out] = accumulator(in).asScala
          final override def onComplete(): IterableOnce[Out] = accumulator match {
            case acc: javadsl.StatefulMapConcatAccumulator[In, Out] @unchecked => acc.onComplete().asScala
            case acc: StatefulMapConcatAccumulator[In, Out] @unchecked         => acc.onComplete()
            case _                                                             => Nil
          }
        }
    }
  }

  implicit final class StatefulMapConcatAccumulatorFactoryFromFunction[In, Out](
      val f: () => In => IterableOnce[Out]) extends AnyVal {

    def asFactory: StatefulMapConcatAccumulatorFactory[In, Out] = new StatefulMapConcatAccumulatorFactory[In, Out] {
      override def accumulator(): StatefulMapConcatAccumulator[In, Out] =
        new scaladsl.StatefulMapConcatAccumulator[In, Out] {
          private val accumulator = f()
          import scala.jdk.CollectionConverters._
          final override def apply(in: In): IterableOnce[Out] = accumulator(in)
          final override def onComplete(): IterableOnce[Out] = accumulator match {
            case acc: javadsl.StatefulMapConcatAccumulator[In, Out] @unchecked => acc.onComplete().asScala
            case acc: StatefulMapConcatAccumulator[In, Out] @unchecked         => acc.onComplete()
            case _                                                             => Nil
          }
        }
    }
  }
}
