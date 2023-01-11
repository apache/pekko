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
