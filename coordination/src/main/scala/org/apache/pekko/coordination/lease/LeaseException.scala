/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.coordination.lease

class LeaseException(message: String) extends RuntimeException(message)

final class LeaseTimeoutException(message: String) extends LeaseException(message)
