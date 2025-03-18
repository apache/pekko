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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.dispatch.Dispatchers

object AbstractRuntime {
  private val interruptedFailure = Failure(new InterruptedException("Fiber interrupted."))
}

abstract class AbstractRuntime(val materializer: Materializer, val clock: ClockDef) {
  import AbstractRuntime._

  private val executor = materializer.system.dispatchers.lookup(Dispatchers.DefaultDispatcherId)
  private val blockingExecutor = materializer.system.dispatchers.lookup(Dispatchers.DefaultBlockingDispatcherId)

  protected def run[T](fiber: FiberRuntime[_], task: TaskDef[T])(onComplete: Try[T] => Unit): Unit = {
    task match {
      // The tasks below respond differently when a fiber is interrupted (rather than just being safely skipped).
      case InterruptabilityDef(interruptable, mkTask) =>
        val (prev, callbacks) = fiber.setInterruptable(interruptable)
        run(fiber, callbacks) { _ =>
          val restorer = new RestorerDef {
            override def apply[U](task: TaskDef[U]) = InterruptabilityDef(prev, _ => task)
          }
          run(fiber, mkTask(restorer)) { res =>
            run(fiber, fiber.setInterruptable(prev)._2)(_ => onComplete(res))
          }
        }

      case OnInterruptDef(base, handler) =>
        run(fiber, fiber.onInterrupt(handler)) { _ =>
          run(fiber, base) { res =>
            fiber.removeOnInterrupt(handler)
            onComplete(res)
          }
        }

      case m @ MapDef(base, fn) =>
        def go(res: Try[m.Base]): Unit = onComplete(Try(fn.apply(res)).flatMap(t => t))

        run(fiber, base) { res =>
          if (fiber.shouldInterrupt) {
            go(interruptedFailure)
          } else {
            go(res)
          }
        }

      case f @ FlatMapDef(base, fn) =>
        def go(res: Try[f.Base]): Unit = executor.execute(() => {
          Try(fn.apply(res)) match {
            case Success(next) => run(fiber, next)(onComplete)
            case Failure(x)    => onComplete(Failure(x))
          }
        })

        run(fiber, base) { res =>
          if (fiber.shouldInterrupt) {
            go(interruptedFailure)
          } else {
            go(res)
          }
        }

      case _ =>
        // The tasks below can be safely skipped when a fiber is interrupted.
        if (fiber.shouldInterrupt) {
          onComplete(interruptedFailure)
        } else task match {
          case GetRuntimeDef =>
            onComplete(Success(this))

          case CallbackDef(launch) =>
            val promise = new PromiseDef[T]
            val callback: (Try[T]) => Unit = promise.complete(_)
            val cancel: TaskDef[Any] = launch(callback).andThen(TaskDef.succeed(onComplete(interruptedFailure)))
            run(fiber, fiber.onInterrupt(cancel)) { _ =>
              promise.onComplete { res =>
                onComplete(res)
                fiber.removeOnInterrupt(cancel)
              }
            }
          case ValueDef(fn, dispatcher) =>
            val ex = if (dispatcher eq Dispatchers.DefaultDispatcherId)
              executor
            else if (dispatcher eq Dispatchers.DefaultBlockingDispatcherId)
              blockingExecutor
            else
              materializer.system.dispatchers.lookup(dispatcher)
            ex.execute(() => {
              onComplete(Try(fn.apply()).flatMap(r => r))
            })
          case f @ ForkDef(task) =>
            val childFiber = new FiberRuntime[f.Res]()
            run(childFiber, task) { res =>
              childFiber.result.complete(res)
            }
            onComplete(Success(FiberDef(childFiber)))
          case InterruptDef(cancelledFiber) =>
            run(fiber, cancelledFiber.interruptNow()) { _ =>
              cancelledFiber.onComplete { onComplete(_) }
            }
          case AwaitDef(promise) =>
            promise.onComplete { res =>
              onComplete(res)
            }
          case CompleteDef(promise, task) =>
            run(fiber, task) { res =>
              promise.complete(res)
              onComplete(Success(()))
            }
          case MapDef(_, _)              => throw new IllegalStateException // Unreachable, handled above
          case FlatMapDef(_, _)          => throw new IllegalStateException // Unreachable, handled above
          case InterruptabilityDef(_, _) => throw new IllegalStateException // Unreachable, handled above
          case OnInterruptDef(_, _)      => throw new IllegalStateException // Unreachable, handled above
        }
    }
  }
}
