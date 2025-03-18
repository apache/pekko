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

trait AbstractResource[+T] {
  def definition: ResourceDef[T]
}

object ResourceDef {
  def acquireRelease[T](acquire: TaskDef[T])(release: T => TaskDef[_]): ResourceDef[T] =
    ResourceDef(acquire.map(t => (t, release(t).unit)))

  def succeedTask[T](task: => TaskDef[T]) = ResourceDef(task.map(value => (value, TaskDef.unit)))

  def succeed[T](value: => T) = ResourceDef(TaskDef.succeed((value, TaskDef.unit)))

  def all[T](elements: Iterable[ResourceDef[T]]): ResourceDef[Seq[T]] = elements.size match {
    case 0 => succeed(Seq.empty)
    case 1 => elements.head.map(Vector(_))
    case _ => elements.foldLeft(succeed(Seq.empty[T]))((result, elem) => result.flatMap(seq => elem.map(t => seq :+ t)))
  }
}

case class ResourceDef[+T](create: TaskDef[(T, TaskDef[Unit])]) {
  def map[U](fn: T => U): ResourceDef[U] = ResourceDef(create.map { case (t, cleanup) => (fn(t), cleanup) })

  def mapTask[U](fn: T => TaskDef[U]): ResourceDef[U] = ResourceDef(create.flatMap { case (t, cleanup) =>
    fn(t).map(u => (u, cleanup))
  })

  def flatMap[U](fn: T => ResourceDef[U]): ResourceDef[U] = ResourceDef(create.flatMap { case (t, cleanupT) =>
    fn(t).create.map { case (u, cleanupU) =>
      (u, cleanupU.andThen(cleanupT))
    }
  })

  def zip[U, R](that: ResourceDef[U], combine: (T, U) => R): ResourceDef[R] = flatMap(t => that.map(u => combine(t, u)))

  def use[U](fn: T => TaskDef[U]): TaskDef[U] = {
    TaskDef.uninterruptableMask[U](restore =>
      create.flatMap { case (t, cleanup) =>
        restore(fn(t)).onComplete(_ => cleanup)
      })
  }

  def fork: ResourceDef[FiberDef[T]] =
    ResourceDef.acquireRelease(ForkDef(create))(fiber => InterruptDef(fiber).flatMap(_._2)).map(_.map(_._1))
}
