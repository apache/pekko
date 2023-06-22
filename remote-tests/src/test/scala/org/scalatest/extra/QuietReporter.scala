/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.scalatest.extra

import java.lang.Boolean.getBoolean

import org.scalatest.events._
import org.scalatest.tools.StandardOutReporter

class QuietReporter(inColor: Boolean, withDurations: Boolean = false)
    extends StandardOutReporter(withDurations, inColor, false, true, false, false, false, false, false, false, false) {

  def this() = this(!getBoolean("pekko.test.nocolor"), !getBoolean("pekko.test.nodurations"))

  override def apply(event: Event): Unit = event match {
    case _: RunStarting => ()
    case _              => super.apply(event)
  }
}
