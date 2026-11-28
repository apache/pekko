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

package org.apache.pekko.remote.artery

import org.apache.pekko.testkit.PekkoSpec

class LatencyMeasurementsSpec extends PekkoSpec {
  "Latency measurements" must {
    "retain fixed deadlines when the sender is delayed" in {
      val schedule = new LatencyMeasurements.SendSchedule(1000000000L, 1000)
      schedule.deadline(0) should ===(1001000000L)
      schedule.deadline(1) should ===(1002000000L)
      // A pause beyond both deadlines must not reset the schedule.
      val measurements = new LatencyMeasurements(2)
      measurements.recordSend(0, schedule.deadline(0), 1005000000L)
      measurements.recordEcho(0, 1006000000L)
      measurements.roundTrip.getValueAtPercentile(50) should be >= 1000000L
      measurements.roundTrip.getValueAtPercentile(50) should be < 1001000L
      measurements.scheduledToEcho.getValueAtPercentile(50) should be >= 5000000L
      measurements.sendLateness.getValueAtPercentile(50) should be >= 4000000L
      schedule.deadline(2) should ===(1003000000L)
    }

    "reject out of order and duplicate IDs without recording invalid samples" in {
      val measurements = new LatencyMeasurements(2)
      measurements.recordSend(0, 10L, 12L)
      measurements.recordSend(1, 20L, 22L)
      intercept[IllegalArgumentException](measurements.recordEcho(-1, 25L))
      intercept[IllegalArgumentException](measurements.recordEcho(2, 25L))
      intercept[IllegalArgumentException](measurements.recordEcho(1, 25L))
      measurements.receivedCount should ===(0)
      measurements.roundTrip.getTotalCount should ===(0L)
      measurements.recordEcho(0, 25L)
      intercept[IllegalArgumentException](measurements.recordEcho(0, 26L))
      measurements.receivedCount should ===(1)
      measurements.recordEcho(1, 30L)
      intercept[IllegalArgumentException](measurements.recordEcho(2, 31L))
      measurements.roundTrip.getTotalCount should ===(2L)
    }

    "retain samples beyond the previous ten-second histogram limit" in {
      val measurements = new LatencyMeasurements(2)
      measurements.recordSend(0, 0L, 20000000000L)
      measurements.recordEcho(0, 20001000000L)
      measurements.roundTrip.getTotalCount should ===(1L)
      measurements.scheduledToEcho.getTotalCount should ===(1L)
      measurements.sendLateness.getTotalCount should ===(1L)
      measurements.scheduledToEcho.getValueAtPercentile(50) should be >= 20001000000L
    }

    "report identical RTT and scheduled latency for an on-time send" in {
      val measurements = new LatencyMeasurements(2)
      measurements.recordSend(0, 10L, 10L)
      measurements.recordEcho(0, 30L)
      measurements.roundTrip.getValueAtPercentile(50) should ===(20L)
      measurements.scheduledToEcho.getValueAtPercentile(50) should ===(20L)
      measurements.sendLateness.getValueAtPercentile(50) should ===(0L)
    }

    "measure send rate using the intervals between sends" in {
      val measurements = new LatencyMeasurements(2)
      measurements.recordSend(0, 0L, 10L)
      measurements.recordSend(1, 1000000000L, 1000000010L)
      measurements.sendRate should ===(1.0)
    }

    "support monotonic clock wraparound when computing elapsed time" in {
      val measurements = new LatencyMeasurements(2)
      val planned = Long.MaxValue - 10
      measurements.recordSend(0, planned, planned + 5)
      measurements.recordEcho(0, planned + 20)
      measurements.roundTrip.getValueAtPercentile(50) should ===(15L)
      measurements.scheduledToEcho.getValueAtPercentile(50) should ===(20L)
    }

    "reject invalid rates and early sends" in {
      intercept[IllegalArgumentException](new LatencyMeasurements.SendSchedule(0, 0))
      intercept[IllegalArgumentException](new LatencyMeasurements(1))
      val measurements = new LatencyMeasurements(2)
      intercept[IllegalArgumentException](measurements.recordSend(0, 10L, 9L))
    }
  }
}
