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

import java.util.concurrent.atomic.AtomicLongArray

import org.HdrHistogram.Histogram

object LatencyMeasurements {

  /** A fixed cadence: a delayed send does not move subsequent deadlines. */
  final class SendSchedule(origin: Long, messagesPerSecond: Int) {
    require(messagesPerSecond > 0 && messagesPerSecond <= 1000000000)
    private val interval = 1000000000L / messagesPerSecond
    def deadline(index: Int): Long = origin + (index.toLong + 1) * interval
  }
}

/** Measurements shared by one sender and one receiver in the latency benchmark. */
final class LatencyMeasurements(totalMessages: Int) {
  require(totalMessages >= 2)

  private val plannedTimes = new Array[Long](totalMessages)
  // Publishing the actual send time also publishes the corresponding planned time.
  private val sendTimes = new AtomicLongArray(totalMessages)
  private var received = 0

  val roundTrip = newHistogram()
  val scheduledToEcho = newHistogram()
  val sendLateness = newHistogram()

  private def newHistogram(): Histogram = {
    val histogram = new Histogram(10000000000L, 3)
    histogram.setAutoResize(true)
    histogram
  }

  def receivedCount: Int = received

  def recordSend(index: Int, planned: Long, actual: Long): Unit = {
    require(actual - planned >= 0, "Message sent before its scheduled time")
    plannedTimes(index) = planned
    sendTimes.set(index, actual)
  }

  /** Called only by the receiver. Reject gaps and duplicates before adding a sample. */
  def recordEcho(id: Long, now: Long): Unit = {
    require(received < totalMessages, "Received too many messages")
    require(id == received, s"Expected message $received but received $id")
    val sent = sendTimes.get(received)
    val planned = plannedTimes(received)
    require(now - sent >= 0, "Echo arrived before its send timestamp")
    roundTrip.recordValue(now - sent)
    scheduledToEcho.recordValue(now - planned)
    sendLateness.recordValue(sent - planned)
    received += 1
  }

  /** Called by the sender after the last send. */
  def sendRate: Double =
    1e9 * (totalMessages - 1) / math.max(1L, sendTimes.get(totalMessages - 1) - sendTimes.get(0))
}
