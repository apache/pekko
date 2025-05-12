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

import java.time.Duration
import java.time.Instant
import org.apache.pekko.util.JavaDurationConverters._
import scala.util.Success

trait ClockDef {
  def nanoTime: TaskDef[java.lang.Long]
  def now: TaskDef[Instant]
  def sleep(duration: Duration): TaskDef[Unit]
}

object ClockDef {
  val nanoTime: TaskDef[java.lang.Long] = GetRuntimeDef.flatMap(_.clock.nanoTime)
  val now: TaskDef[Instant] = GetRuntimeDef.flatMap(_.clock.now)
  def sleep(duration: Duration): TaskDef[Unit] = GetRuntimeDef.flatMap(_.clock.sleep(duration))

  val system: ClockDef = new ClockDef {
    override def nanoTime = TaskDef.succeed(System.nanoTime)
    override def now = TaskDef.succeed(Instant.now())
    override def sleep(duration: Duration) = GetRuntimeDef.flatMap(runtime =>
      CallbackDef { cb =>
        val scheduled = runtime.materializer.scheduleOnce(duration.asScala,
          () => {
            cb(Success(()))
          })
        TaskDef.succeed(() -> {
          scheduled.cancel()
        })
      }
    )
  }
}
