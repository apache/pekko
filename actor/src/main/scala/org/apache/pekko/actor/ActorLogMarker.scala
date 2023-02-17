/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.event.LogMarker

/**
 * This is public with the purpose to document the used markers and properties of log events.
 * No guarantee that it will remain binary compatible, but the marker names and properties
 * are considered public API and will not be changed without notice.
 */
@ApiMayChange
object ActorLogMarker {

  /**
   * Marker "pekkoDeadLetter" of log event for dead letter messages.
   *
   * @param messageClass The message class of the DeadLetter. Included as property "pekkoMessageClass".
   */
  def deadLetter(messageClass: String): LogMarker =
    LogMarker("pekkoDeadLetter", Map(LogMarker.Properties.MessageClass -> messageClass))

}
