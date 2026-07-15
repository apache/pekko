/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl.streamref

import scala.concurrent.duration.FiniteDuration

import org.apache.pekko

import pekko.annotation.InternalApi

/** INTERNAL API */
@InternalApi
private[pekko] final case class StreamRefDefaultSettings(
    bufferCapacity: Int,
    demandRedeliveryInterval: FiniteDuration,
    subscriptionTimeout: FiniteDuration,
    finalTerminationSignalDeadline: FiniteDuration)
