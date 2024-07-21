/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.journal

import org.apache.pekko
import pekko.util.ccompat.JavaConverters._

/**
 * The journal may support tagging of events that are used by the
 * `EventsByTag` query and it may support specifying the tags via an
 * [[pekko.persistence.journal.EventAdapter]] that wraps the events
 * in a `Tagged` with the given `tags`. The journal may support other
 * ways of doing tagging. Please consult the documentation of the specific
 * journal implementation for more information.
 *
 * The journal will unwrap the event and store the `payload`.
 */
case class Tagged(payload: Any, tags: Set[String]) {

  /**
   * Java API
   */
  def this(payload: Any, tags: java.util.Set[String]) =
    this(payload, tags.asScala.toSet)
}
