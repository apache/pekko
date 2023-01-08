/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.metrics.sample

import org.apache.pekko.serialization.jackson.CborSerializable

//#messages
final case class StatsJob(text: String) extends CborSerializable
final case class StatsResult(meanWordLength: Double) extends CborSerializable
final case class JobFailed(reason: String) extends CborSerializable
//#messages
