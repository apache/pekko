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

package org.apache.pekko.stream.testkit

import org.apache.pekko.testkit.TestKitBase
import org.scalatest.time.{ Millis, Span }

import java.util.concurrent.TimeUnit

trait StreamConfiguration extends TestKitBase {
  case class StreamConfig(allStagesStoppedTimeout: Span = Span({
          val c = system.settings.config.getConfig("pekko.stream.testkit")
          c.getDuration("all-stages-stopped-timeout", TimeUnit.MILLISECONDS)
        }, Millis))

  private val defaultStreamConfig = StreamConfig()

  /**
   * The default `StreamConfig` which is derived from the Actor System's `pekko.stream.testkit.all-stages-stopped-timeout`
   * configuration value. If you want to provide a different StreamConfig for specific tests without having to re-specify
   * `pekko.stream.testkit.all-stages-stopped-timeout` then you can override this value.
   */
  implicit def streamConfig: StreamConfig = defaultStreamConfig

}
