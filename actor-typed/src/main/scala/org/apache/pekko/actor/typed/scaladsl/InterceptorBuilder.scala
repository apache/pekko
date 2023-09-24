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

package org.apache.pekko.actor.typed.scaladsl

import org.apache.pekko
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.BehaviorInterceptor
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.internal.BehaviorImpl
import org.apache.pekko.actor.typed.scaladsl.Behaviors.Supervise
import org.apache.pekko.actor.typed.scaladsl.Behaviors.ThrowableClassTag

import scala.reflect.ClassTag
import scala.reflect.classTag

/**
 * Immutable builder for [[pekko.actor.typed.scaladsl.Behaviors.intercept]]
 */
object InterceptorBuilder {
  def apply[T](initialBehavior: Behavior[T]): InterceptorBuilder[T] = {
    new InterceptorBuilder[T](initialBehavior)
  }
}

class InterceptorBuilder[T](initialBehavior: Behavior[T]) {

  private var currentBehavior: Behavior[T] = initialBehavior


  /**
   * Specify the [[SupervisorStrategy]] to be invoked when the wrapped behavior throws. The simple way of as [[Behaviors.supervise]]
   */
  def onFailure[Thr <: Throwable](strategy: SupervisorStrategy)(
    implicit tag: ClassTag[Thr] = ThrowableClassTag): InterceptorBuilder[T] = {
    currentBehavior = new Supervise[T](currentBehavior).onFailure(strategy)(classTag)
    this
  }

  /**
   * Intercept messages and signals for a `behavior` by first passing them to a [[pekko.actor.typed.BehaviorInterceptor]]
   * The simple way of as [[Behaviors.intercept]]
   */
  def intercept[O](behaviorInterceptor: () => BehaviorInterceptor[O, T]): InterceptorBuilder[O] =
    new InterceptorBuilder(BehaviorImpl.intercept(behaviorInterceptor)(currentBehavior))

  /**
   * Build the final Behavior from the current state of the builder
   */
  def build(): Behavior[T] = {
    currentBehavior
  }


}
