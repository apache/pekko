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

import scala.concurrent.Promise
import scala.util.{ Failure, Success, Try }

import org.apache.pekko.stream.Materializer

class RuntimeDef(mat: Materializer) {
  import RuntimeDef._
  val executor = mat.executionContext

  // TODO move to ScalaDSL
  def runToPromise[T](task: AbstractTask[T]): Promise[T] = {
    val p = Promise[T]()
    run(new FiberDef[T](), task.definition, (res: Try[T]) => p.complete(res))
    p
  }

  // TODO move to JavaDSL
  def runAsync[T](task: AbstractTask[T]): CompletableFuture[T] = {
    val f = new CompletableFuture[T]()
    run(new FiberDef[T](), task.definition,
      (res: Try[T]) =>
        res match {
          case Success(t) => f.complete(t)
          case Failure(x) => f.completeExceptionally(x)
        })
    f
  }

  private def run[T](fiber: FiberDef[_], task: TaskDef[T], onComplete: Try[T] => Unit): Unit = {
    task match {
      // The tasks below respond differently when a fiber is interrupted (rather than just being safely skipped).

      case InterruptabilityDef(interruptable, mkTask) =>
        val prevState = fiber.state.get.interruptable
        fiber.state.getAndUpdate(_.copy(interruptable = interruptable))
        val restorer = new Restorer {
          override def apply[U](task: TaskDef[U]) = InterruptabilityDef(prevState, (_: Restorer) => task)
        }
        run(fiber, mkTask(restorer),
          { (res: Try[T]) =>
            fiber.state.getAndUpdate(_.copy(interruptable = prevState))
            onComplete(res)
          })

      case m @ MapDef(base, fn) =>
        def go(res: Try[m.Base]): Unit = onComplete(Try(fn.apply(res)).flatMap(t => t))

        run(fiber, base,
          (res: Try[m.Base]) => {
            if (fiber.shouldInterrupt) {
              go(interruptedFailure)
            } else {
              go(res)
            }
          })

      case f @ FlatMapDef(base, fn) =>
        def go(res: Try[f.Base]): Unit = executor.execute(() => {
          Try(fn.apply(res)) match {
            case Success(next) => run(fiber, next, onComplete)
            case Failure(x)    => onComplete(Failure(x))
          }
        })

        run(fiber, base,
          (res: Try[f.Base]) => {
            if (fiber.shouldInterrupt) {
              go(interruptedFailure)
            } else {
              go(res)
            }
          })

      case _ =>
        // The tasks below can be safely skipped when a fiber is interrupted.
        if (fiber.shouldInterrupt) {
          onComplete(interruptedFailure)
        } else task match {
          case g @ GraphDef(graph) =>
            val (killswitch, future) = mat.materialize(graph)
            // This returns a Fiber, which when cancelled should cancel the graph as well.
            val childFiber = new FiberDef[g.Res] {
              override def onInterrupt() = {
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
          case f @ ForkDef(task) =>
            val childFiber = new FiberDef[f.Res]()
            run(childFiber, task,
              (res: Try[f.Res]) =>
                childFiber.complete(res)
            )
            onComplete(Success(childFiber))
          case JoinDef(joinedFiber) =>
            joinedFiber.onComplete { res =>
              onComplete(res)
            }
          case InterruptDef(cancelledFiber) =>
            cancelledFiber.interrupt()
            cancelledFiber.onComplete { _ =>
              onComplete(Success(()))
            }
          case MapDef(_, _)              => throw new IllegalStateException // Unreachable, handled abbove
          case FlatMapDef(_, _)          => throw new IllegalStateException // Unreachable, handled abbove
          case InterruptabilityDef(_, _) => throw new IllegalStateException // Unreachable, handled abbove
        }
    }
  }
}

object RuntimeDef {
  val interruptedFailure = Failure(new InterruptedException("Fiber interrupted."))
}
