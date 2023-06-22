/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.stage

import org.apache.pekko
import pekko.event.LoggingAdapter
import pekko.event.NoLogging
import pekko.stream.MaterializerLoggingProvider

/**
 * Simple way to obtain a [[pekko.event.LoggingAdapter]] when used together with an [[pekko.stream.Materializer]].
 * If used with a different materializer [[pekko.event.NoLogging]] will be returned.
 *
 * Make sure to only access `log` from GraphStage callbacks (such as `pull`, `push` or the async-callback).
 *
 * Note, abiding to [[pekko.stream.ActorAttributes.logLevels]] has to be done manually,
 * the logger itself is configured based on the logSource provided to it. Also, the `log`
 * itself would not know if you're calling it from a "on element" context or not, which is why
 * these decisions have to be handled by the operator itself.
 */
trait StageLogging { self: GraphStageLogic =>
  private[this] var _log: LoggingAdapter = _

  /** Override to customise reported log source */
  protected def logSource: Class[_] = this.getClass

  def log: LoggingAdapter = {
    // only used in StageLogic, i.e. thread safe
    if (_log eq null) {
      materializer match {
        case p: MaterializerLoggingProvider =>
          _log = p.makeLogger(logSource.asInstanceOf[Class[Any]])
        case _ =>
          _log = NoLogging
      }
    }
    _log
  }

}
