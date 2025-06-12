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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.task.ClockDef
import org.apache.pekko.task.AbstractRuntime
import org.apache.pekko.task.AbstractTask
import scala.concurrent.Future
import org.apache.pekko.task.FiberRuntime

object Runtime {
  def apply(materializer: Materializer, clock: ClockDef = ClockDef.system): Runtime = new Runtime(materializer, clock)
}

class Runtime(materializer: Materializer, clock: ClockDef = ClockDef.system)
    extends AbstractRuntime(materializer, clock) {
  def runAsync[T](task: AbstractTask[T]): Future[T] = {
    val p = scala.concurrent.Promise[T]()
    run(new FiberRuntime[T](), task.definition)(res => p.complete(res))
    p.future
  }
}
