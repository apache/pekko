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

import scala.util.{ Failure, Success, Try }
import java.time.Duration
import org.apache.pekko.dispatch.Dispatchers
import org.apache.pekko.stream.Graph
import org.apache.pekko.stream.ClosedShape
import java.util.concurrent.atomic.AtomicInteger

trait AbstractTask[+T] {
  def definition: TaskDef[T]
}

object TaskDef {
  def run[T](graph: Graph[ClosedShape, RunningGraph[T]]): TaskDef[T] = GetRuntimeDef.flatMap { runtime =>
    val running = runtime.materializer.materialize(graph)
    OnInterruptDef(running.completion.definition, running.interrupt.definition)
  }
  def fromTry[T](res: => Try[T]) = ValueDef(() => res)
  def fail[T](x: => Throwable): TaskDef[T] = ValueDef(() => Failure(x))
  def succeed[T](t: => T): TaskDef[T] = ValueDef(() => Success(t))
  def succeedOn[T](dispatcher: String)(t: => T): TaskDef[T] = ValueDef(() => Success(t), dispatcher)
  def unit: TaskDef[Unit] = ValueDef(() => Success(()))
  def narrow[T](t: TaskDef[? <: T]): TaskDef[T] = t
  def never[T]: TaskDef[T] = CallbackDef(_ => unit)

  def uninterruptableMask[T](fn: RestorerDef => TaskDef[T]): TaskDef[T] = {
    InterruptabilityDef(false, r => fn(r))
  }

  /** Returns a task that runs all the given task in sequence, returning all results. */
  def all[T](elements: Iterable[TaskDef[T]]): TaskDef[Seq[T]] = elements.size match {
    case 0 => succeed(Seq.empty)
    case 1 => elements.head.map(Vector(_))
    case _ => elements.foldLeft(succeed(Seq.empty[T]))((result, elem) => result.flatMap(seq => elem.map(t => seq :+ t)))
  }

  /** Returns a task that runs all the given task in parallel, returning all results. If any task fails, the rest is interrupted. */
  def allPar[T](tasks: Iterable[TaskDef[T]]): TaskDef[Seq[T]] = (for {
    done <- PromiseDef.make[Unit].toResource
    waiting = new AtomicInteger(tasks.size)
    fibers <- ResourceDef.all(tasks.toVector.map(task =>
      forkResource(task.onComplete { res =>
        TaskDef.succeed {
          val remaining = waiting.decrementAndGet()
          if (res.isFailure) {
            done.complete(Failure(res.failed.get))
          } else if (remaining <= 0) {
            done.complete(Success(()))
          }
        }
      })))
    _ <- done.await.toResource
    r <- TaskDef.all(fibers.map(_.join)).toResource
  } yield r).use(t => TaskDef.succeed(t))

  def forkDaemon[T](task: TaskDef[T]): TaskDef[FiberDef[T]] = ForkDef(task)

  def forkResource[T](task: TaskDef[T]): ResourceDef[FiberDef[T]] =
    ResourceDef.acquireRelease[FiberDef[T]](ForkDef(task))(InterruptDef(_))

  /**
   * Returns a Task which executes all given tasks in parallel, returning whichever of them
   * completes first, and the interrupts the rest.
   */
  def raceAll[T](tasks: Iterable[TaskDef[T]]): TaskDef[T] = (for {
    result <- PromiseDef.make[T].toResource
    _ <- ResourceDef.all(tasks.toVector.map(task => forkResource(result.completeWith(task))))
    r <- result.await.toResource
  } yield r).use(t => TaskDef.succeed(t))
}
sealed trait TaskDef[+T] {
  import TaskDef._

  def map[U](fn: T => U): TaskDef[U] = MapDef(this, (res: Try[T]) => res.map(fn))
  def as[U](value: U): TaskDef[U] = map(_ => value)
  def unit: TaskDef[Unit] = as(())

  def zipPar[U](that: TaskDef[U]): TaskDef[(T, U)] =
    TaskDef.allPar(Seq(this, that)).map(res => (res(0).asInstanceOf[T], res(1).asInstanceOf[U]))

  def asResult[U](result: Try[U]): TaskDef[U] = flatMapResult(_ => fromTry(result))

  def flatMap[U](fn: T => TaskDef[U]): TaskDef[U] = FlatMapDef(this,
    (_: Try[T]) match {
      case Success(t) => fn(t)
      case Failure(x) => fail(x)
    })

  def flatMapResult[U](fn: Try[T] => TaskDef[U]): TaskDef[U] =
    FlatMapDef[T, U](this, res => fn(res))

  def flatMapError[T1 >: T](fn: Throwable => TaskDef[T1]): TaskDef[T1] = FlatMapDef(this,
    (_: Try[T]) match {
      case Success(t) => succeed(t)
      case Failure(x) => fn(x)
    })

  def onComplete(fn: Try[T] => TaskDef[?]): TaskDef[T] =
    FlatMapDef[T, T](this, res => fn(res).asResult(res))

  def andThen[U](that: TaskDef[U]): TaskDef[U] = FlatMapDef(this, (_: Try[T]) => that)

  def before(that: TaskDef[Any]): TaskDef[T] = FlatMapDef(this, (res: Try[T]) => that.asResult(res))

  def toResource: ResourceDef[T] = ResourceDef(this.map(t => (t, TaskDef.unit)))

  def after(duration: Duration) = GetRuntimeDef.flatMap { rt => rt.clock.sleep(duration) }.andThen(this)

  def catchSome[T1 >: T](pf: PartialFunction[Throwable, T1]): TaskDef[T1] = catchSomeWith(pf.andThen(succeed(_)))

  def catchSomeWith[T1 >: T](pf: PartialFunction[Throwable, TaskDef[T1]]): TaskDef[T1] =
    flatMapError(x => pf.applyOrElse(x, (_: Throwable) => fail(x)))
}

/** A TaskDef that retrieves the current runtime */
case object GetRuntimeDef extends TaskDef[AbstractRuntime]

/** A TaskDef that attaches a temporary extra onInterrupt handler to a given task */
case class OnInterruptDef[T](base: TaskDef[T], onInterrupt: TaskDef[_]) extends TaskDef[T]

/**
 * A TaskDef that turns a callback into a Task. The [launch] function receives a "callback" argument which
 *  can be used to complete the task. [launch] should return a task itself, which will be invoked to interrupt
 *  the task.
 */
case class CallbackDef[T](launch: (Try[T] => Unit) => TaskDef[_]) extends TaskDef[T] {
  type Res = T
}

/** A TaskDef that runs a function to return a value. */
case class ValueDef[+T](value: () => Try[T], dispatcher: String = Dispatchers.DefaultDispatcherId) extends TaskDef[T]

/** A TaskDef that runs a function with the result of another TaskDef as input, returning a new value */
case class MapDef[T, U](base: TaskDef[T], fn: Try[T] => Try[U]) extends TaskDef[U] {
  type Base = T
}

/** A TaskDef that runs a function with the result of another TaskDef as input, returning another TaskDef */
case class FlatMapDef[T, U](base: TaskDef[T], fn: Try[T] => TaskDef[U]) extends TaskDef[U] {
  type Base = T
}

/** A TaskDef that waits for the given promise to complete, returning its result. */
case class AwaitDef[T](promise: PromiseDef[T]) extends TaskDef[T]

/** A TaskDef that completes a promise with the result of a task. */
case class CompleteDef[T](promise: PromiseDef[T], result: TaskDef[T]) extends TaskDef[Unit] {
  type Target = T
}

/** A TaskDef that starts running another TaskDef in the background, returning a Fiber to interact with that process. */
case class ForkDef[T](task: TaskDef[T]) extends TaskDef[FiberDef[T]] {
  type Res = T
}

/**
 * A TaskDef that interrupts a background process, completing when
 * that process has completely stopped. The returned task holds the
 * fiber result if it was already completed, or a failed
 * InterruptException if the fiber was interrupted.
 */
case class InterruptDef[T](fiber: FiberDef[T]) extends TaskDef[T] {
  type Res = T
}

//case class OnCompleteDef[T](fiber: FiberDef[T], fn: Try[T] => TaskDef[Unit]) extends TaskDef[Unit]

trait RestorerDef {
  def apply[T](task: TaskDef[T]): TaskDef[T]
}

/**
 * A TaskDef that changes whether the fiber running it is allowed to be interrupted. [mkTask] is
 *  invoked to create the actual task to run. That function can use the [Restorer] argument to wrap tasks
 *  that should run with the earlier value of the interruptable flag.
 */
case class InterruptabilityDef[T](interruptable: Boolean, mkTask: RestorerDef => TaskDef[T]) extends TaskDef[T]
