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

package org.apache.pekko.stream.javadsl

import org.apache.pekko
import pekko.NotUsed
import pekko.japi.Pair
import pekko.japi.function

object Keep {
  private val _left = new function.Function2[Any, Any, Any] with ((Any, Any) => Any) { def apply(l: Any, r: Any) = l }
  private val _right = new function.Function2[Any, Any, Any] with ((Any, Any) => Any) { def apply(l: Any, r: Any) = r }
  private val _both = new function.Function2[Any, Any, Any] with ((Any, Any) => Any) {
    def apply(l: Any, r: Any) = new pekko.japi.Pair(l, r)
  }
  private val _none = new function.Function2[Any, Any, NotUsed] with ((Any, Any) => NotUsed) {
    def apply(l: Any, r: Any) = NotUsed
  }

  def left[L, R]: function.Function2[L, R, L] = _left.asInstanceOf[function.Function2[L, R, L]]
  def right[L, R]: function.Function2[L, R, R] = _right.asInstanceOf[function.Function2[L, R, R]]
  def both[L, R]: function.Function2[L, R, L Pair R] = _both.asInstanceOf[function.Function2[L, R, L Pair R]]
  def none[L, R]: function.Function2[L, R, NotUsed] = _none.asInstanceOf[function.Function2[L, R, NotUsed]]
}
