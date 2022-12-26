/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.testkit

import scala.util.control.NoStackTrace

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.ActorRefWithCell

object Utils {

  /** Sets the default-mailbox to the usual [[pekko.dispatch.UnboundedMailbox]] instead of [[StreamTestDefaultMailbox]]. */
  val UnboundedMailboxConfig =
    ConfigFactory.parseString(
      """pekko.actor.default-mailbox.mailbox-type = "org.apache.pekko.dispatch.UnboundedMailbox"""")

  case class TE(message: String) extends RuntimeException(message) with NoStackTrace

  def assertDispatcher(ref: ActorRef, dispatcher: String): Unit = ref match {
    case r: ActorRefWithCell =>
      if (r.underlying.props.dispatcher != dispatcher)
        throw new AssertionError(
          s"Expected $ref to use dispatcher [$dispatcher], yet used: [${r.underlying.props.dispatcher}]")
    case _ =>
      throw new Exception(s"Unable to determine dispatcher of $ref")
  }
}
