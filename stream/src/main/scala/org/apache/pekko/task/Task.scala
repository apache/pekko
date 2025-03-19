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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.{ Failure, Success, Try }

import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.Graph
import org.apache.pekko.stream.KillSwitch
import org.apache.pekko.stream.Materializer

/*
 *  Cancellation is not exposed yet. In order to add it, we need the following:
 *  - Feedback from KillSwitch on when a graph has actually fully stopped
 *  - Access to ExecutorService for our dispatchers (not just Executor), so we don't need to rely on Future.
 */
abstract class Task[+T] {
  def definition: TaskDef[T]
}

sealed trait TaskDef[+T]
object TaskDef {
  def narrow[T](t: TaskDef[? <: T]): TaskDef[T] = t
}

case class GraphDef[T](graph: Graph[ClosedShape, (KillSwitch, Future[T])]) extends TaskDef[FiberImpl[T]] {
  type Res = T
}

case class ValueDef[+T](value: () => Try[T]) extends TaskDef[T]

case class MapDef[T, U](base: TaskDef[T], fn: Try[T] => Try[U]) extends TaskDef[U] {
  type Base = T
}

case class FlatMapDef[T, U](base: TaskDef[T], fn: Try[T] => TaskDef[U]) extends TaskDef[U] {
  type Base = T
}

case class ForkDef[T](task: TaskDef[T]) extends TaskDef[FiberImpl[T]] {
  type Res = T
}

case class JoinDef[T](fiber: FiberImpl[T]) extends TaskDef[T]

case class CancelDef[T](fiber: FiberImpl[T]) extends TaskDef[Unit]

class FiberImpl[T] {
  sealed trait FiberState
  case class Running(onComplete: Seq[Try[T] => Unit] = Seq.empty) extends FiberState
  case class Completed(result: Try[T]) extends FiberState
  case object Cancelled extends FiberState

  protected def onCancel(): Unit = {}
  val state = new AtomicReference[FiberState](Running())

  def cancel() = {
    // We currently invoke onCancel on every cancellation attempt. We might need to revisit that.
    onCancel()
    completeTo(Cancelled, FiberImpl.cancellationFailure)
  }
  def complete(res: Try[T]) = completeTo(Completed(res), res)

  private def completeTo(newState: FiberState, res: Try[T]): Unit = {
    // Complete the state, but only if it's still running
    val s = state.getAndUpdate(_ match {
      case Running(_) => newState
      case other      => other
    })

    s match {
      case Running(onComplete: Seq[Try[T] => Unit]) =>
        for (callback <- onComplete) {
          callback(res)
        }
      case _ =>
    }
  }

  def onComplete(f: Try[T] => Unit) = {
    // Either it's already completed (don't add a listener), or it is completed (add a listener)
    val s = state.getAndUpdate(_ match {
      case Running(callbacks) => Running(callbacks :+ f)
      case other              => other
    })

    // Directly apply the callback if we're already completed
    s match {
      case Running(_) =>
      // No action now, we've added the callback
      case Completed(res) =>
        f.apply(res)
      case Cancelled =>
        f.apply(FiberImpl.cancellationFailure)
    }
  }

  def isCancelled = state.get == Cancelled
}

object FiberImpl {
  val cancellationFailure = Failure(new InterruptedException("Fiber cancelled."))
}

class Runtime(mat: Materializer) {
  val executor = mat.executionContext

  // TODO move to ScalaDSL
  def runToPromise[T](task: Task[T]): Promise[T] = {
    val p = Promise[T]()
    run(new FiberImpl[T](), task.definition, (res: Try[T]) => p.complete(res))
    p
  }

  // TODO move to JavaDSL
  def runAsync[T](task: Task[T]): CompletableFuture[T] = {
    val f = new CompletableFuture[T]()
    run(new FiberImpl[T](), task.definition,
      (res: Try[T]) =>
        res match {
          case Success(t) => f.complete(t)
          case Failure(x) => f.completeExceptionally(x)
        })
    f
  }

  private def run[T](fiber: FiberImpl[_], task: TaskDef[T], onComplete: Try[T] => Unit): Unit = {
    if (fiber.isCancelled) onComplete(FiberImpl.cancellationFailure)
    else task match {
      case g @ GraphDef(graph) =>
        val (killswitch, future) = mat.materialize(graph)
        // This returns a Fiber, which when cancelled should cancel the graph as well.
        val childFiber = new FiberImpl[g.Res] {
          override def onCancel() = {
            killswitch.shutdown()
          }
        }
        future.onComplete { res =>
          childFiber.complete(res)
        }(executor)
        onComplete(Success(childFiber))
      case ValueDef(fn) =>
        executor.execute(() => {
          onComplete(Try(fn.apply()).flatMap(r => r))
        })
      case m @ MapDef(base, fn) =>
        run(fiber, base,
          (res: Try[m.Base]) =>
            onComplete(Try(fn.apply(res)).flatMap(t => t))
        )
      case f @ FlatMapDef(base, fn) =>
        run(fiber, base,
          (res: Try[f.Base]) =>
            executor.execute(() => {
              Try(fn.apply(res)) match {
                case Success(next) => run(fiber, next, onComplete)
                case Failure(x)    => onComplete(Failure(x))
              }
            })
        )
      case f @ ForkDef(task) =>
        val childFiber = new FiberImpl[f.Res]()
        run(childFiber, task,
          (res: Try[f.Res]) =>
            childFiber.complete(res)
        )
        onComplete(Success(childFiber))
      case JoinDef(joinedFiber) =>
        joinedFiber.onComplete(res =>
          executor.execute(() =>
            onComplete(res)
          )
        )
      case CancelDef(cancelledFiber) =>
        cancelledFiber.cancel()
        executor.execute(() =>
          onComplete(Success(()))
        )
    }
  }
}
