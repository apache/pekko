/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.state.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.compat.java8.OptionConverters._

import org.apache.pekko
import pekko.persistence.state.scaladsl.{ GetObjectResult => SGetObjectResult }

/**
 * API for reading durable state objects with payload `A`.
 *
 * For Scala API see [[pekko.persistence.state.scaladsl.DurableStateStore]].
 *
 * See also [[DurableStateUpdateStore]]
 */
trait DurableStateStore[A] {

  def getObject(persistenceId: String): CompletionStage[GetObjectResult[A]]

}

final case class GetObjectResult[A](value: Optional[A], revision: Long) {
  def toScala: SGetObjectResult[A] = SGetObjectResult(value.asScala, revision)
}
