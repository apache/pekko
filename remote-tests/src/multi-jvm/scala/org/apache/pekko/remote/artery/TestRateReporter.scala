/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import java.util.concurrent.TimeUnit.SECONDS

class TestRateReporter(name: String)
    extends RateReporter(SECONDS.toNanos(1),
      new RateReporter.Reporter {
        override def onReport(
            messagesPerSec: Double,
            bytesPerSec: Double,
            totalMessages: Long,
            totalBytes: Long): Unit = {
          if (totalBytes > 0) {
            println(
              name +
              f": $messagesPerSec%,.0f msgs/sec, $bytesPerSec%,.0f bytes/sec, " +
              f"totals $totalMessages%,d messages ${totalBytes / (1024 * 1024)}%,d MB")
          } else {
            println(
              name +
              f": $messagesPerSec%,.0f msgs/sec " +
              f"total $totalMessages%,d messages")
          }
        }
      }) {}
