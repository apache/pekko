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

package org.apache.pekko.testkit.metrics

import java.lang.management.ManagementFactory

import com.sun.management.UnixOperatingSystemMXBean

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.codahale.metrics.Gauge

class FileDescriptorMetricSetSpec extends AnyWordSpec with Matchers {

  "FileDescriptorMetricSet" must {
    "read file descriptor counts through the public management interface" in {
      val os = ManagementFactory.getOperatingSystemMXBean
      if (!os.isInstanceOf[UnixOperatingSystemMXBean]) cancel("UnixOperatingSystemMXBean is not available")

      val metrics = new FileDescriptorMetricSet(os).getMetrics
      val open = metrics.get("file-descriptors.open").asInstanceOf[Gauge[Long]].getValue
      val max = metrics.get("file-descriptors.max").asInstanceOf[Gauge[Long]].getValue

      open should be >= 0L
      max should be >= open
    }
  }
}
