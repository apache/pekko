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

package org.apache.pekko.task.scaladsl

import org.apache.pekko.task.AbstractTask
import org.apache.pekko.task.RestorerDef
import org.apache.pekko.task.TaskDef

import scala.util.Try

object Task {
  def fromTry[T](res: => Try[T]) = Task(TaskDef.fromTry(res))

  def fail[T](x: => Throwable): Task[T] = Task(TaskDef.fail(x))

  def succeed[T](value: => T): Task[T] = Task(TaskDef.succeed(value))

  val unit: Task[Unit] = Task(TaskDef.unit)

  trait Restorer {
    def apply[T](task: AbstractTask[T]): Task[T]
  }
  object Restorer {
    def apply(r: RestorerDef) = new Restorer {
      override def apply[T](task: AbstractTask[T]): Task[T] = Task(r.apply(task.definition))
    }
  }

  def uninterruptableMask[T](fn: Restorer => AbstractTask[T]): Task[T] = {
    Task(TaskDef.uninterruptableMask(restorerDef => fn(Restorer(restorerDef)).definition))
  }

  def all[T](tasks: Iterable[Task[T]]): Task[Seq[T]] = Task(TaskDef.all(tasks.map(_.definition)))

  def raceAll[T](tasks: Iterable[Task[T]]): Task[T] = Task(TaskDef.raceAll(tasks.map(_.definition)))

  private[scaladsl] def task[T](t: AbstractTask[T]): Task[T] =
    if (t.isInstanceOf[Task[T]]) t.asInstanceOf[Task[T]] else Task(t.definition)
}

case class Task[+T](definition: TaskDef[T]) extends AbstractTask[T] {
  import Task._

  def map[U](fn: T => U): Task[U] = Task(definition.map(fn))

  def as[U](value: U): Task[U] = map(_ => value)

  def asResult[U](result: Try[U]): Task[U] = flatMapResult(_ => fromTry(result))

  def unit: Task[Unit] = as(())

  def flatMap[U](fn: T => AbstractTask[U]): Task[U] = Task(definition.flatMap(fn.andThen(_.definition)))

  def zipWith[U, R](that: AbstractTask[U])(combine: (T, U) => R): Task[R] =
    flatMap(t => task(that).map(u => combine(t, u)))

  def zip[U](that: AbstractTask[U]): Task[(T, U)] = zipWith(that)((_, _))

  def andThen[U](that: AbstractTask[U]): Task[U] = flatMap(_ => that)

  def flatMapResult[U](fn: Try[T] => AbstractTask[U]): Task[U] =
    Task(definition.flatMapResult(fn.andThen(_.definition)))

  def onComplete(fn: Try[T] => AbstractTask[?]) = Task(definition.onComplete(fn.andThen(_.definition)))

  def forkDaemon: Task[Fiber[T]] = Task(TaskDef.forkDaemon(definition).map(f => new Fiber(f)))

  def forkResource: Resource[Fiber[T]] = Resource(TaskDef.forkResource(definition).map(f => new Fiber(f)))

  def toResource: Resource[T] = Resource.succeedTask(this)
}
