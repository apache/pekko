/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import org.apache.pekko

package object javadsl {
  def combinerToScala[M1, M2, M](f: pekko.japi.function.Function2[M1, M2, M]): (M1, M2) => M =
    f match {
      case x if x eq Keep.left   => scaladsl.Keep.left.asInstanceOf[(M1, M2) => M]
      case x if x eq Keep.right  => scaladsl.Keep.right.asInstanceOf[(M1, M2) => M]
      case s: Function2[_, _, _] => s.asInstanceOf[(M1, M2) => M]
      case other                 => other.apply _
    }
}
