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
