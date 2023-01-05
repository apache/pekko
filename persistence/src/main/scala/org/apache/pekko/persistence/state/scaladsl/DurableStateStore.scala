/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.state.scaladsl

import scala.concurrent.Future
import scala.compat.java8.OptionConverters._

import org.apache.pekko
import pekko.persistence.state.javadsl.{ GetObjectResult => JGetObjectResult }

/**
 * API for reading durable state objects with payload `A`.
 *
 * For Java API see [[pekko.persistence.state.javadsl.DurableStateStore]].
 *
 * See also [[DurableStateUpdateStore]]
 */
trait DurableStateStore[A] {

  def getObject(persistenceId: String): Future[GetObjectResult[A]]

}

final case class GetObjectResult[A](value: Option[A], revision: Long) {
  def toJava: JGetObjectResult[A] = JGetObjectResult(value.asJava, revision)
}
