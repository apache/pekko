/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl.streamref

import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.event.Logging
import pekko.stream.StreamRefSettings

/** INTERNAL API */
@InternalApi
private[pekko] final case class StreamRefSettingsImpl(
    override val bufferCapacity: Int,
    override val demandRedeliveryInterval: FiniteDuration,
    override val subscriptionTimeout: FiniteDuration,
    override val finalTerminationSignalDeadline: FiniteDuration)
    extends StreamRefSettings {

  override def withBufferCapacity(value: Int): StreamRefSettings = copy(bufferCapacity = value)
  override def withDemandRedeliveryInterval(value: FiniteDuration): StreamRefSettings =
    copy(demandRedeliveryInterval = value)
  override def withSubscriptionTimeout(value: FiniteDuration): StreamRefSettings = copy(subscriptionTimeout = value)
  override def withTerminationReceivedBeforeCompletionLeeway(value: FiniteDuration): StreamRefSettings =
    copy(finalTerminationSignalDeadline = value)

  override def productPrefix: String = Logging.simpleName(classOf[StreamRefSettings])

}
