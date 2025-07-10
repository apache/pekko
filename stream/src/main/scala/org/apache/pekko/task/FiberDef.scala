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
import scala.collection.immutable.HashSet
import scala.util.Try

trait FiberDef[+T] { base =>
  def join: TaskDef[T]

  def interruptAndGet: TaskDef[T] = InterruptDef(this)

  def interrupt: TaskDef[Unit] = interruptAndGet.unit.catchSome {
    case _: InterruptedException => ()
  }

  private[task] def onComplete(handler: Try[T] => Unit): Unit
  private[task] def interruptNow(): TaskDef[Unit]

  def map[U](fn: T => U): FiberDef[U] = new FiberDef[U] {
    override def join = base.join.map(fn)
    override def onComplete(handler: Try[U] => Unit) = base.onComplete(res => handler(res.map(fn)))
    override def interruptNow() = base.interruptNow()
  }
}

object FiberDef {
  def apply[T](rt: FiberRuntime[T]): FiberDef[T] = new FiberDef[T] {
    override def join = AwaitDef(rt.result)
    override def onComplete(handler: Try[T] => Unit) = rt.result.onComplete(handler)
    override def interruptNow() = rt.interruptNow()
  }
}

object FiberRuntime {
  type OnInterrupt = TaskDef[_]
}

class FiberRuntime[T] {
  import FiberRuntime._

  val result = new PromiseDef[T]

  case class State(
      interruptable: Boolean = true,
      interrupted: Boolean = false,
      onInterrupt: Set[OnInterrupt] = HashSet.empty
  ) {
    def shouldInterrupt: Boolean = interruptable && interrupted

    def interrupt() = copy(interrupted = true)

    def addOnInterrupt(handler: OnInterrupt) = copy(onInterrupt = onInterrupt + handler)
    def removeOnInterrupt(handler: OnInterrupt) = copy(onInterrupt = onInterrupt - handler)
  }

  private def invokeOnInterrupt(): TaskDef[Unit] = {
    val s = state.getAndUpdate(s => if (s.shouldInterrupt) s.copy(onInterrupt = HashSet.empty) else s)
    if (s.shouldInterrupt) {
      s.onInterrupt.foldLeft(TaskDef.unit)(_.before(_))
    } else {
      TaskDef.unit
    }
  }

  def onInterrupt(handler: OnInterrupt): TaskDef[Unit] = {
    state.updateAndGet(_.addOnInterrupt(handler))
    invokeOnInterrupt()
  }

  def removeOnInterrupt(handler: OnInterrupt): Unit = {
    state.updateAndGet(_.removeOnInterrupt(handler))
  }

  private val state = new AtomicReference(State())

  /** Sends the interrupt signal and invokes handlers. Doesn't join on the fiber. */
  private[task] def interruptNow(): TaskDef[Unit] = {
    // We only set the interrupted flag if we're still running.
    result.whenRunning {
      state.getAndUpdate(_.interrupt())
    }

    // Invoke the handlers if we can interrupt now as well. Otherwise, "withInterruptable" will take care of it later.
    invokeOnInterrupt()
  }

  def shouldInterrupt: Boolean = state.get.shouldInterrupt

  /** Sets the interruptable flag, and returns the old state of the flag and any callbacks that must be made now. */
  private[task] def setInterruptable(i: Boolean): (Boolean, TaskDef[Unit]) = {
    val s = state.getAndUpdate(_.copy(interruptable = i))
    (s.interruptable, invokeOnInterrupt())
  }
}
