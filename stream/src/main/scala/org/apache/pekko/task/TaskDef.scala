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

package org.apache.pekko.task

import scala.concurrent.Future
import scala.util.{ Success, Try }

import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.Graph
import org.apache.pekko.stream.KillSwitch

abstract class AbstractTask[+T] {
  def definition: TaskDef[T]
}

sealed trait TaskDef[+T] {
  def andThen[U](that: TaskDef[U]): TaskDef[U] = FlatMapDef(this, (_: Try[T]) => that)
}
object TaskDef {
  def succeed[T](t: => T): TaskDef[T] = ValueDef(() => Success(t))
  def unit: TaskDef[Unit] = ValueDef(() => Success(()))
  def narrow[T](t: TaskDef[? <: T]): TaskDef[T] = t
}

/** A TaskDef that runs a graph that has the ability to be cancelled. */
case class GraphDef[T](graph: Graph[ClosedShape, (KillSwitch, Future[T])]) extends TaskDef[FiberDef[T]] {
  type Res = T
}

/** A TaskDef that runs a function to return a value. */
case class ValueDef[+T](value: () => Try[T]) extends TaskDef[T]

/** A TaskDef that runs a function with the result of another TaskDef as input, returning a new value */
case class MapDef[T, U](base: TaskDef[T], fn: Try[T] => Try[U]) extends TaskDef[U] {
  type Base = T
}

/** A TaskDef that runs a function with the result of another TaskDef as input, returning another TaskDef */
case class FlatMapDef[T, U](base: TaskDef[T], fn: Try[T] => TaskDef[U]) extends TaskDef[U] {
  type Base = T
}

/** A TaskDef that starts running another TaskDef in the background, returning a Fiber to interact with that process. */
case class ForkDef[T](task: TaskDef[T]) extends TaskDef[FiberDef[T]] {
  type Res = T
}

/** A TaskDef that waits on a background process to complete, yielding its success (or failure) */
case class JoinDef[T](fiber: FiberDef[T]) extends TaskDef[T]

/** A TaskDef that interrupts a background process, completing when that process has completely stopped. */
case class InterruptDef[T](fiber: FiberDef[T]) extends TaskDef[Unit]

trait Restorer {
  def apply[T](task: TaskDef[T]): TaskDef[T]
}

/**
 * A TaskDef that changes whether the fiber running it is allowed to be interrupted. [mkTask] is
 *  invoked to create the actual task to run. That function can use the [Restorer] argument to wrap tasks
 *  that should run with the earlier value of the interruptable flag.
 */
case class InterruptabilityDef[T](interruptable: Boolean, mkTask: Restorer => TaskDef[T]) extends TaskDef[T]
