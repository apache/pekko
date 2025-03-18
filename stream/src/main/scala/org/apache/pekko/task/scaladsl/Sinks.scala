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
import org.apache.pekko.stream.scaladsl.Sink
import scala.concurrent.Future
import org.apache.pekko.Done
import org.apache.pekko.dispatch.ExecutionContexts

object Sinks {
  def foreach[T](fn: T => AbstractTask[Unit]): Sink[T, Future[Done]] = {
    Sink.fromMaterializer { (mat, _) =>
      val runtime = Runtime(mat)
      Sink.foreachAsync[T](1)(t => runtime.runAsync(fn(t)))
    }.mapMaterializedValue(f => f.flatMap(r => r)(ExecutionContexts.parasitic))
  }
}
