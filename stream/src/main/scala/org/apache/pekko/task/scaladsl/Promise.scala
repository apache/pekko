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

import org.apache.pekko.task.AwaitDef
import org.apache.pekko.task.CompleteDef
import org.apache.pekko.task.PromiseDef

object Promise {
  def make[T]: Task[Promise[T]] = Task.succeed(new Promise(new PromiseDef()))
}

class Promise[T](definition: PromiseDef[T]) {
  def await: Task[T] = Task(AwaitDef(definition))

  def completeWith(task: Task[T]): Task[Unit] = Task(CompleteDef(definition, task.definition))

  def succeed(result: => T) = completeWith(Task.succeed(result))

  def fail(x: => Throwable) = completeWith(Task.fail(x))
}
