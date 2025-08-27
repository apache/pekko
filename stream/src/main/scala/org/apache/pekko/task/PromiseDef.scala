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

import scala.util.Try
import java.util.concurrent.atomic.AtomicReference

object PromiseDef {
  def make[T]: TaskDef[PromiseDef[T]] = TaskDef.succeed(new PromiseDef)
}

class PromiseDef[T] {
  type OnComplete = Try[T] => Unit

  val state = new AtomicReference(State())

  sealed trait Completion
  case class Running(onComplete: Seq[OnComplete] = Seq.empty) extends Completion {
    def addOnComplete(f: OnComplete) = copy(onComplete = onComplete :+ f)
    def invokeOnComplete(res: Try[T]): Unit = for (f <- onComplete) f(res)
  }
  case class Completed(result: Try[T]) extends Completion
  case class State(completion: Completion = Running()) {
    def complete(res: Try[T]) = completion match {
      case Running(_) => copy(completion = Completed(res))
      case _          => this
    }
    def addOnComplete(f: OnComplete) = completion match {
      case r @ Running(_) => copy(completion = r.addOnComplete(f))
      case _              => this
    }
  }

  def whenRunning(f: => Unit) = {
    state.getAndUpdate(s =>
      s match {
        case State(Running(_)) => f; s
        case _                 => s
      })
  }

  def completeWith(task: TaskDef[T]): TaskDef[Unit] = CompleteDef(this, task)

  def await: TaskDef[T] = AwaitDef(this)

  def complete(res: Try[T]): Unit = {
    // Complete the state, but only if it's still running
    val s = state.getAndUpdate(_.complete(res))

    s match {
      case State(r @ Running(_)) =>
        // Invoke callbacks if we were running before
        r.invokeOnComplete(res)
      case _ =>
      // We were already completed, so nothing to do.
    }
  }

  def onComplete(f: OnComplete): Unit = {
    // Either it's already completed (don't add a listener), or it is completed (add a listener)
    val s = state.getAndUpdate(_.addOnComplete(f))

    // Directly apply the callback if we're already completed
    s match {
      case State(Completed(res)) =>
        f.apply(res)
      case _ =>
    }
  }
}
