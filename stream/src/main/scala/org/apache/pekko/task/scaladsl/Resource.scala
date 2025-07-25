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
import org.apache.pekko.task.ResourceDef
import org.apache.pekko.task.AbstractResource

object Resource {
  def acquireRelease[T](acquire: AbstractTask[T], release: T => AbstractTask[_]): Resource[T] =
    Resource(ResourceDef.acquireRelease(acquire.definition)(release.andThen(_.definition)))

  def succeed[T](value: => T) = Resource(ResourceDef.succeed(value))

  def succeedTask[T](task: => AbstractTask[T]) = Resource(ResourceDef.succeedTask(task.definition))

  def all[T](elements: Iterable[AbstractResource[T]]): Resource[Seq[T]] =
    Resource(ResourceDef.all(elements.map(_.definition)))

  private[scaladsl] def resource[T](r: AbstractResource[T]): Resource[T] =
    if (r.isInstanceOf[Resource[T]]) r.asInstanceOf[Resource[T]] else Resource(r.definition)
}

case class Resource[+T](definition: ResourceDef[T]) extends AbstractResource[T] {
  import Resource._

  def map[U](fn: T => U): Resource[U] = Resource(definition.map(fn))

  def mapTask[U](fn: T => AbstractTask[U]): Resource[U] = Resource(definition.mapTask(fn.andThen(_.definition)))

  def flatMap[U](fn: T => AbstractResource[U]): Resource[U] = Resource(definition.flatMap(fn.andThen(_.definition)))

  def zipWith[U, R](that: AbstractResource[U])(combine: (T, U) => R): Resource[R] =
    flatMap(t => resource(that).map(u => combine(t, u)))

  def zip[U](that: Resource[U]): Resource[(T, U)] = zipWith(that)((_, _))

  def use[U](fn: T => AbstractTask[U]): Task[U] = Task(definition.use(fn.andThen(_.definition)))
}
