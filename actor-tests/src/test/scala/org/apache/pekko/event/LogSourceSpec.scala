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

package org.apache.pekko.event

import org.apache.pekko
import pekko.testkit.PekkoSpec

object LogSourceSpec {
  class LogsWithGetClass(system: pekko.actor.ActorSystem) {
    // `getClass` returns `Class[? <: LogsWithGetClass]`, which did not compile on Scala 3
    val log: LoggingAdapter = Logging(system, getClass)
    val logSource: (String, Class[?]) = LogSource(getClass, system)
  }
}

class LogSourceSpec extends PekkoSpec {
  import LogSourceSpec._

  "LogSource" must {

    "resolve an implicit LogSource for the result of getClass" in {
      val subject = new LogsWithGetClass(system)
      subject.logSource._1 should ===(s"LogSourceSpec$$LogsWithGetClass($system)")
      subject.logSource._2 should ===(classOf[LogsWithGetClass])
      subject.log.isInstanceOf[BusLogging] should ===(true)
    }

    "resolve an implicit LogSource for a class literal" in {
      val logSource: (String, Class[?]) = LogSource(classOf[LogsWithGetClass])
      logSource._1 should ===("LogSourceSpec$LogsWithGetClass")
      logSource._2 should ===(classOf[LogsWithGetClass])
    }

    "resolve an implicit LogSource for a wildcard class" in {
      val c: Class[?] = classOf[LogsWithGetClass]
      val logSource: (String, Class[?]) = LogSource(c)
      logSource._1 should ===("LogSourceSpec$LogsWithGetClass")
      logSource._2 should ===(classOf[LogsWithGetClass])
    }
  }
}
