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

import java.util.concurrent.atomic.AtomicReference

import scala.util.Try

class FiberDef[T] {
  type OnComplete = Try[T] => Unit

  sealed trait Completion {
    def interrupted: Boolean
  }
  case class Running(onComplete: Seq[OnComplete] = Seq.empty, interrupted: Boolean = false) extends Completion {
    def addOnComplete(f: OnComplete) = copy(onComplete = onComplete :+ f)
    def invokeOnComplete(res: Try[T]): Unit = for (f <- onComplete) f(res)
  }
  case class Completed(result: Try[T], interrupted: Boolean) extends Completion {}

  case class FiberState(interruptable: Boolean = true, completion: Completion = Running()) {
    def shouldInterrupt: Boolean = interruptable && completion.interrupted

    def complete(res: Try[T]) = completion match {
      case Running(_, interrupted) => copy(completion = Completed(res, interrupted))
      case _                       => this
    }

    def addOnComplete(f: OnComplete) = completion match {
      case r @ Running(_, _) => copy(completion = r.addOnComplete(f))
      case _                 => this
    }

    def interrupt() = completion match {
      case r @ Running(_, _) => copy(completion = r.copy(interrupted = true))
      case _                 => this
    }
  }

  protected def onInterrupt(): Unit = {}
  val state = new AtomicReference(FiberState())

  // Only sends the interrupt signal. Doesn't join on the fiber.
  def interrupt(): Unit = {
    state.getAndUpdate(_.interrupt())
    // We currently invoke onInterrupt on every interrupt attempt. We might need to revisit that.
    onInterrupt()
  }

  def complete(res: Try[T]): Unit = {
    // Complete the state, but only if it's still running
    val s = state.getAndUpdate(_.complete(res))

    s match {
      case FiberState(_, r @ Running(_, _)) =>
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
      case FiberState(_, Completed(res, _)) =>
        f.apply(res)
      case _ =>
    }
  }

  def shouldInterrupt: Boolean = state.get.shouldInterrupt
}
