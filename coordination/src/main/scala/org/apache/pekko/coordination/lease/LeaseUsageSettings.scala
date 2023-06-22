/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.coordination.lease

import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.util.JavaDurationConverters._
import pekko.util.PrettyDuration._

final class LeaseUsageSettings private[pekko] (val leaseImplementation: String,
    val leaseRetryInterval: FiniteDuration) {
  def getLeaseRetryInterval(): java.time.Duration = leaseRetryInterval.asJava

  override def toString = s"LeaseUsageSettings($leaseImplementation, ${leaseRetryInterval.pretty})"
}
